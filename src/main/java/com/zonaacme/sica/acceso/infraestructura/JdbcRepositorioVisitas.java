package com.zonaacme.sica.acceso.infraestructura;

import com.zonaacme.sica.acceso.aplicacion.OcupanteActual;
import com.zonaacme.sica.acceso.aplicacion.RepositorioVisitas;
import com.zonaacme.sica.acceso.aplicacion.SolicitudPendiente;
import com.zonaacme.sica.acceso.aplicacion.VisitaAbiertaResumen;
import com.zonaacme.sica.acceso.dominio.EstadoVisita;
import com.zonaacme.sica.acceso.dominio.TipoVisita;
import com.zonaacme.sica.acceso.dominio.Visita;
import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;
import com.zonaacme.sica.shared.infraestructura.CatalogoEstados;
import com.zonaacme.sica.shared.infraestructura.ConexionBD;
import com.zonaacme.sica.shared.infraestructura.ContextoJdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcRepositorioVisitas implements RepositorioVisitas {

    private static final String COLUMNAS = """
            id, persona_id, empresa_destino_id, anfitrion_usuario_id, tipo_visita,
            estado_visita_id, motivo, fecha_programada, fecha_entrada, fecha_salida,
            registrada_por_usuario_id, visita_aprobada_por, fecha_aprobacion, observaciones
            """;

    /**
     * FOR UPDATE es la pieza que sostiene la invariante del sistema.
     * MySQL no permite un indice unico parcial del tipo
     *     UNIQUE (persona_id) WHERE estado = 'Dentro'
     * asi que la unicidad se hace cumplir bloqueando la fila mientras dura la
     * transaccion de ingreso. Si dos porterias registran a la misma persona al
     * mismo tiempo, la segunda espera y ve el resultado de la primera.
     */
    private static final String SQL_ABIERTA_BLOQUEANDO =
            "SELECT " + COLUMNAS + " FROM visitas WHERE persona_id = ? AND estado_visita_id = ? FOR UPDATE";

    private static final String SQL_APROBADA_VIGENTE = """
            SELECT %s FROM visitas
             WHERE persona_id = ? AND estado_visita_id = ?
               AND (fecha_programada IS NULL OR DATE(fecha_programada) <= CURDATE())
             ORDER BY fecha_programada IS NULL, fecha_programada
             LIMIT 1
            """.formatted(COLUMNAS);

    private static final String SQL_POR_ID =
            "SELECT " + COLUMNAS + " FROM visitas WHERE id = ?";

    private static final String SQL_INSERTAR = """
            INSERT INTO visitas
                (persona_id, empresa_destino_id, anfitrion_usuario_id, tipo_visita, estado_visita_id,
                 motivo, fecha_programada, fecha_entrada, fecha_salida,
                 registrada_por_usuario_id, visita_aprobada_por, fecha_aprobacion, observaciones)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_ACTUALIZAR = """
            UPDATE visitas
               SET estado_visita_id = ?, fecha_entrada = ?, fecha_salida = ?,
                   visita_aprobada_por = ?, fecha_aprobacion = ?, observaciones = ?
             WHERE id = ?
            """;

    private static final String SQL_PENDIENTES_EMPRESA = """
            SELECT v.id, v.persona_id, v.tipo_visita, v.motivo, v.fecha_creacion,
                   v.estado_visita_id,
                   p.nombre AS nombre,
                   p.tipo_documento, p.documento_identidad, p.url_foto,
                   e.nombre AS empresa
              FROM visitas v
              JOIN personas p ON p.id = v.persona_id
              JOIN empresas e ON e.id = v.empresa_destino_id
             WHERE v.empresa_destino_id = ?
               AND v.estado_visita_id IN (?, ?)
             ORDER BY v.fecha_creacion ASC
            """;

    private static final String SQL_OCUPACION = """
            SELECT visita_id, persona_id, tipo_documento, documento_identidad, nombre_completo,
                   tipo_persona, empresa_destino, torre, fecha_entrada, minutos_dentro
              FROM v_ocupacion_actual
             ORDER BY fecha_entrada ASC
            """;

    // ------------------------------------------------------------------
    // Operaciones transaccionales
    // ------------------------------------------------------------------

    @Override
    public Optional<Visita> buscarAbiertaBloqueando(ContextoTransaccion contexto, long personaId) {
        return buscarUna(ContextoJdbc.conexionDe(contexto), SQL_ABIERTA_BLOQUEANDO,
                         personaId, CatalogoEstados.idDe(EstadoVisita.DENTRO));
    }

    @Override
    public Optional<Visita> buscarAprobadaVigente(ContextoTransaccion contexto, long personaId) {
        return buscarUna(ContextoJdbc.conexionDe(contexto), SQL_APROBADA_VIGENTE,
                         personaId, CatalogoEstados.idDe(EstadoVisita.APROBADA));
    }

    @Override
    public Optional<Visita> buscarPorId(ContextoTransaccion contexto, long visitaId) {
        return buscarUna(ContextoJdbc.conexionDe(contexto), SQL_POR_ID, visitaId);
    }

    private Optional<Visita> buscarUna(Connection conexion, String sql,
                                       long parametro, int... estados) {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, parametro);
            for (int i = 0; i < estados.length; i++) {
                sentencia.setInt(i + 2, estados[i]);
            }
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next() ? Optional.of(mapear(fila)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar la visita.", e);
        }
    }

    @Override
    public Visita crear(ContextoTransaccion contexto, Visita visita) {
        Connection conexion = ContextoJdbc.conexionDe(contexto);

        try (PreparedStatement sentencia =
                     conexion.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setLong(1, visita.personaId());
            sentencia.setLong(2, visita.empresaDestinoId());
            ponerLong(sentencia, 3, visita.anfitrionUsuarioId());
            sentencia.setString(4, visita.tipo().name());
            sentencia.setInt(5, CatalogoEstados.idDe(visita.estado()));
            sentencia.setString(6, visita.motivo());
            ponerFecha(sentencia, 7, visita.fechaProgramada());
            ponerFecha(sentencia, 8, visita.fechaIngreso());
            ponerFecha(sentencia, 9, visita.fechaSalida());
            ponerLong(sentencia, 10, visita.registradaPorUsuarioId());
            ponerLong(sentencia, 11, visita.aprobadaPorUsuarioId());
            ponerFecha(sentencia, 12, visita.fechaAprobacion());
            sentencia.setString(13, visita.observaciones());

            sentencia.executeUpdate();

            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("MySQL no devolvio el id generado de la visita.");
                }
                visita.asignarId(claves.getLong(1));
            }
            return visita;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo crear la visita.", e);
        }
    }

    @Override
    public void actualizar(ContextoTransaccion contexto, Visita visita) {
        Connection conexion = ContextoJdbc.conexionDe(contexto);

        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_ACTUALIZAR)) {
            sentencia.setInt(1, CatalogoEstados.idDe(visita.estado()));
            ponerFecha(sentencia, 2, visita.fechaIngreso());
            ponerFecha(sentencia, 3, visita.fechaSalida());
            ponerLong(sentencia, 4, visita.aprobadaPorUsuarioId());
            ponerFecha(sentencia, 5, visita.fechaAprobacion());
            sentencia.setString(6, visita.observaciones());
            sentencia.setLong(7, visita.id());
            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo actualizar la visita.", e);
        }
    }

    // ------------------------------------------------------------------
    // Consultas de solo lectura
    // ------------------------------------------------------------------

    @Override
    public Optional<VisitaAbiertaResumen> visitaAbiertaDe(long personaId) {
        String sql = "SELECT id, fecha_entrada FROM visitas "
                   + " WHERE persona_id = ? AND estado_visita_id = ? LIMIT 1";

        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {

            sentencia.setLong(1, personaId);
            sentencia.setInt(2, CatalogoEstados.idDe(EstadoVisita.DENTRO));
            try (ResultSet fila = sentencia.executeQuery()) {
                if (!fila.next()) {
                    return Optional.empty();
                }
                Timestamp ingreso = fila.getTimestamp("fecha_entrada");
                return Optional.of(new VisitaAbiertaResumen(
                        fila.getLong("id"),
                        ingreso == null ? null : ingreso.toLocalDateTime()));
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("Fallo la consulta de la visita abierta.", e);
        }
    }

    @Override
    public Optional<Long> idDeVisitaAprobadaVigente(long personaId) {
        return primerId("""
                SELECT id FROM visitas
                 WHERE persona_id = ? AND estado_visita_id = ?
                   AND (fecha_programada IS NULL OR DATE(fecha_programada) <= CURDATE())
                 ORDER BY fecha_programada IS NULL, fecha_programada
                 LIMIT 1
                """, personaId, CatalogoEstados.idDe(EstadoVisita.APROBADA));
    }

    @Override
    public boolean tieneSolicitudEnEspera(long personaId) {
        return primerId("""
                SELECT id FROM visitas
                 WHERE persona_id = ?
                   AND estado_visita_id IN (?, ?)
                 LIMIT 1
                """, personaId,
                CatalogoEstados.idDe(EstadoVisita.PENDIENTE_APROBACION),
                CatalogoEstados.idDe(EstadoVisita.PENDIENTE_APROBACION_OLVIDO)).isPresent();
    }

    private Optional<Long> primerId(String sql, long parametro, int... estados) {
        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {

            sentencia.setLong(1, parametro);
            for (int i = 0; i < estados.length; i++) {
                sentencia.setInt(i + 2, estados[i]);
            }
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next() ? Optional.of(fila.getLong(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("Fallo la consulta de visitas.", e);
        }
    }

    @Override
    public List<SolicitudPendiente> pendientesDeEmpresa(long empresaId) {
        List<SolicitudPendiente> solicitudes = new ArrayList<>();

        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_PENDIENTES_EMPRESA)) {

            sentencia.setLong(1, empresaId);
            sentencia.setInt(2, CatalogoEstados.idDe(EstadoVisita.PENDIENTE_APROBACION));
            sentencia.setInt(3, CatalogoEstados.idDe(EstadoVisita.PENDIENTE_APROBACION_OLVIDO));
            try (ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    solicitudes.add(new SolicitudPendiente(
                            fila.getLong("id"),
                            fila.getLong("persona_id"),
                            fila.getString("nombre"),
                            fila.getString("tipo_documento") + " " + fila.getString("documento_identidad"),
                            fila.getString("url_foto"),
                            fila.getString("empresa"),
                            TipoVisita.valueOf(fila.getString("tipo_visita")),
                            CatalogoEstados.estadoDeId(fila.getInt("estado_visita_id")),
                            fila.getString("motivo"),
                            fila.getTimestamp("fecha_creacion").toLocalDateTime()));
                }
            }
            return solicitudes;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudieron consultar las solicitudes pendientes.", e);
        }
    }

    @Override
    public List<OcupanteActual> ocupacionActual() {
        List<OcupanteActual> ocupantes = new ArrayList<>();

        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_OCUPACION);
             ResultSet fila = sentencia.executeQuery()) {

            while (fila.next()) {
                ocupantes.add(new OcupanteActual(
                        fila.getLong("visita_id"),
                        fila.getLong("persona_id"),
                        fila.getString("tipo_documento") + " " + fila.getString("documento_identidad"),
                        fila.getString("nombre_completo"),
                        fila.getString("tipo_persona"),
                        fila.getString("empresa_destino"),
                        fila.getString("torre"),
                        fila.getTimestamp("fecha_entrada").toLocalDateTime(),
                        fila.getLong("minutos_dentro")));
            }
            return ocupantes;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar la ocupacion actual.", e);
        }
    }

    // ------------------------------------------------------------------
    // Mapeo
    // ------------------------------------------------------------------

    /** El modelo usa INT; el dominio usa Long. getObject daria Integer. */
    private Long idOpcional(ResultSet fila, String columna) throws SQLException {
        long valor = fila.getLong(columna);
        return fila.wasNull() ? null : valor;
    }

    private Visita mapear(ResultSet fila) throws SQLException {
        return Visita.rehidratar(
                fila.getLong("id"),
                fila.getLong("persona_id"),
                fila.getLong("empresa_destino_id"),
                idOpcional(fila, "anfitrion_usuario_id"),
                TipoVisita.valueOf(fila.getString("tipo_visita")),
                CatalogoEstados.estadoDeId(fila.getInt("estado_visita_id")),
                fila.getString("motivo"),
                aFecha(fila.getTimestamp("fecha_programada")),
                aFecha(fila.getTimestamp("fecha_entrada")),
                aFecha(fila.getTimestamp("fecha_salida")),
                idOpcional(fila, "registrada_por_usuario_id"),
                idOpcional(fila, "visita_aprobada_por"),
                aFecha(fila.getTimestamp("fecha_aprobacion")),
                fila.getString("observaciones"));
    }

    private LocalDateTime aFecha(Timestamp marca) {
        return marca == null ? null : marca.toLocalDateTime();
    }

    private void ponerLong(PreparedStatement sentencia, int posicion, Long valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(posicion, Types.BIGINT);
        } else {
            sentencia.setLong(posicion, valor);
        }
    }

    private void ponerFecha(PreparedStatement sentencia, int posicion, LocalDateTime valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(posicion, Types.TIMESTAMP);
        } else {
            sentencia.setTimestamp(posicion, Timestamp.valueOf(valor));
        }
    }
}
