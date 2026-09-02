package com.zonaacme.sica.incidentes.infraestructura;

import com.zonaacme.sica.incidentes.aplicacion.ComandoRegistrarIncidente;
import com.zonaacme.sica.incidentes.aplicacion.Incidente;
import com.zonaacme.sica.incidentes.aplicacion.RepositorioIncidentes;
import com.zonaacme.sica.incidentes.dominio.EstadoIncidente;
import com.zonaacme.sica.incidentes.dominio.GravedadIncidente;
import com.zonaacme.sica.incidentes.dominio.TipoIncidente;
import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;
import com.zonaacme.sica.shared.infraestructura.ConexionBD;
import com.zonaacme.sica.shared.infraestructura.ContextoJdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcRepositorioIncidentes implements RepositorioIncidentes {

    private static final String SELECT_BASE = """
            SELECT i.id, i.visita_id, i.persona_id, i.fecha, i.descripcion,
                   i.tipo, i.gravedad, i.estado,
                   p.nombre AS persona, p.documento_identidad,
                   u.nombre AS reportado_por
              FROM incidentes i
              LEFT JOIN personas p ON p.id = i.persona_id
              JOIN usuarios u      ON u.id = i.reportado_por_id
            """;

    @Override
    public long crear(ContextoTransaccion contexto,
                      ComandoRegistrarIncidente comando, long reportadoPorId) {

        String sql = """
                INSERT INTO incidentes
                    (visita_id, persona_id, reportado_por_id, fecha, descripcion,
                     tipo, gravedad, estado)
                VALUES (?, ?, ?, NOW(), ?, ?, ?, 'ABIERTO')
                """;

        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            asignarIdOpcional(sentencia, 1, comando.visitaId());
            asignarIdOpcional(sentencia, 2, comando.personaId());
            sentencia.setLong(3, reportadoPorId);
            sentencia.setString(4, comando.descripcion().trim());
            sentencia.setString(5, comando.tipo().name());
            sentencia.setString(6, comando.gravedad().name());
            sentencia.executeUpdate();

            try (ResultSet clave = sentencia.getGeneratedKeys()) {
                if (!clave.next()) {
                    throw new ExcepcionTecnica("El incidente no devolvio identificador.");
                }
                return clave.getLong(1);
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo registrar el incidente.", e);
        }
    }

    /**
     * La conclusion se anexa a la descripcion en vez de reemplazarla.
     * Sobrescribir el relato original con la conclusion destruiria la evidencia
     * de lo que realmente se observo en el momento.
     */
    @Override
    public void cerrar(ContextoTransaccion contexto, long incidenteId, String conclusion) {
        String sql = """
                UPDATE incidentes
                   SET estado = 'CERRADO',
                       descripcion = CONCAT(descripcion, '\\n\\n[CIERRE] ', ?)
                 WHERE id = ?
                """;

        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(sql)) {

            sentencia.setString(1, conclusion.trim());
            sentencia.setLong(2, incidenteId);
            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo cerrar el incidente.", e);
        }
    }

    @Override
    public Optional<Incidente> buscarPorId(ContextoTransaccion contexto, long incidenteId) {
        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(SELECT_BASE + " WHERE i.id = ?")) {

            sentencia.setLong(1, incidenteId);
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next() ? Optional.of(mapear(fila)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar el incidente.", e);
        }
    }

    /**
     * Los abiertos primero y dentro de ellos los mas graves arriba: quien abre
     * esta pantalla quiere ver lo que esta sin resolver, no lo mas reciente.
     */
    @Override
    public List<Incidente> listarTodos() {
        String sql = SELECT_BASE + """
                 ORDER BY i.estado = 'CERRADO',
                          FIELD(i.gravedad, 'CRITICA','ALTA','MEDIA','BAJA'),
                          i.fecha DESC
                """;

        List<Incidente> incidentes = new ArrayList<>();
        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet fila = sentencia.executeQuery()) {

            while (fila.next()) {
                incidentes.add(mapear(fila));
            }
            return incidentes;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudieron listar los incidentes.", e);
        }
    }

    private Incidente mapear(ResultSet fila) throws SQLException {
        Timestamp fecha = fila.getTimestamp("fecha");
        return new Incidente(
                fila.getLong("id"),
                idOpcional(fila, "visita_id"),
                idOpcional(fila, "persona_id"),
                fila.getString("persona"),
                fila.getString("documento_identidad"),
                fila.getString("reportado_por"),
                fecha == null ? null : fecha.toLocalDateTime(),
                fila.getString("descripcion"),
                TipoIncidente.valueOf(fila.getString("tipo")),
                GravedadIncidente.valueOf(fila.getString("gravedad")),
                EstadoIncidente.valueOf(fila.getString("estado")));
    }

    private void asignarIdOpcional(PreparedStatement sentencia, int posicion, Long valor)
            throws SQLException {
        if (valor == null) {
            sentencia.setNull(posicion, java.sql.Types.INTEGER);
        } else {
            sentencia.setLong(posicion, valor);
        }
    }

    private Long idOpcional(ResultSet fila, String columna) throws SQLException {
        long valor = fila.getLong(columna);
        return fila.wasNull() ? null : valor;
    }
}
