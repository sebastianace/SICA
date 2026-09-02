package com.zonaacme.sica.acceso.infraestructura;

import com.zonaacme.sica.acceso.aplicacion.ConsultaPorteria;
import com.zonaacme.sica.acceso.dominio.PersonaEnPorteria;
import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;
import com.zonaacme.sica.shared.infraestructura.ConexionBD;
import com.zonaacme.sica.shared.infraestructura.CatalogoEstados;
import com.zonaacme.sica.shared.infraestructura.ContextoJdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Adaptador que alimenta la pantalla de la porteria desde la tabla persona. */
public final class JdbcConsultaPorteria implements ConsultaPorteria {

    private static final String SELECT_BASE = """
            SELECT p.id, p.tipo_documento, p.documento_identidad, p.nombre,
                   p.tipo_persona, p.empresa_id, p.url_foto, p.motivo_bloqueo,
                   ea.nombre_estado AS estado_acceso,
                   e.nombre AS empresa_nombre
              FROM personas p
              LEFT JOIN empresas e                ON e.id  = p.empresa_id
              LEFT JOIN persona_estados_acceso ea ON ea.id = p.estado_acceso_id
            """;

    private static final String SQL_POR_DOCUMENTO =
            SELECT_BASE + " WHERE p.tipo_documento = ? AND p.documento_identidad = ?";

    private static final String SQL_BUSQUEDA = SELECT_BASE + """
             WHERE p.documento_identidad LIKE ?
                OR p.nombre LIKE ?
             ORDER BY p.nombre
             LIMIT ?
            """;

    @Override
    public Optional<PersonaEnPorteria> buscarPorDocumento(String tipoDocumento, String numeroDocumento) {
        try (Connection conexion = ConexionBD.abrir()) {
            return buscar(conexion, tipoDocumento, numeroDocumento);
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar la persona.", e);
        }
    }

    @Override
    public Optional<PersonaEnPorteria> buscarPorDocumento(ContextoTransaccion contexto,
                                                          String tipoDocumento, String numeroDocumento) {
        try {
            return buscar(ContextoJdbc.conexionDe(contexto), tipoDocumento, numeroDocumento);
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar la persona dentro de la transaccion.", e);
        }
    }

    private Optional<PersonaEnPorteria> buscar(Connection conexion,
                                               String tipoDocumento, String numeroDocumento)
            throws SQLException {

        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_POR_DOCUMENTO)) {
            sentencia.setString(1, tipoDocumento);
            sentencia.setString(2, numeroDocumento.trim());

            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next() ? Optional.of(mapear(fila)) : Optional.empty();
            }
        }
    }

    @Override
    public List<PersonaEnPorteria> buscar(String texto, int limite) {
        String patron = "%" + texto.trim() + "%";
        List<PersonaEnPorteria> encontradas = new ArrayList<>();

        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_BUSQUEDA)) {

            sentencia.setString(1, patron);
            sentencia.setString(2, patron);
            sentencia.setInt(3, limite);

            try (ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    encontradas.add(mapear(fila));
                }
            }
            return encontradas;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("Fallo la busqueda de personas.", e);
        }
    }

    /** El modelo usa INT; el dominio usa Long. getObject daria Integer. */
    private Long idOpcional(ResultSet fila, String columna) throws SQLException {
        long valor = fila.getLong(columna);
        return fila.wasNull() ? null : valor;
    }

    private PersonaEnPorteria mapear(ResultSet fila) throws SQLException {
        return new PersonaEnPorteria(
                fila.getLong("id"),
                fila.getString("tipo_documento"),
                fila.getString("documento_identidad"),
                fila.getString("nombre"),
                fila.getString("tipo_persona"),
                idOpcional(fila, "empresa_id"),
                fila.getString("empresa_nombre"),
                fila.getString("url_foto"),
                // La traduccion del estado vive en infraestructura: el dominio
                // solo recibe un booleano y no sabe que existe una tabla de
                // estados de acceso.
                CatalogoEstados.estaBloqueado(fila.getString("estado_acceso")),
                fila.getString("motivo_bloqueo"));
    }

    @Override
    public String nombreDePersona(ContextoTransaccion contexto, long personaId) {
        String sql = "SELECT nombre FROM personas WHERE id = ?";
        try (PreparedStatement sentencia =
                     ContextoJdbc.conexionDe(contexto).prepareStatement(sql)) {

            sentencia.setLong(1, personaId);
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next() ? fila.getString(1) : "Persona #" + personaId;
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("Fallo la consulta del nombre de la persona.", e);
        }
    }
}
