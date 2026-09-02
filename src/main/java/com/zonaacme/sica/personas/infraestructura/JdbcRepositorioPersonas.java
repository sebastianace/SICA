package com.zonaacme.sica.personas.infraestructura;

import com.zonaacme.sica.personas.aplicacion.*;
import com.zonaacme.sica.personas.dominio.EstadoAcceso;
import com.zonaacme.sica.personas.dominio.TipoPersona;
import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;
import com.zonaacme.sica.shared.infraestructura.CatalogoEstados;
import com.zonaacme.sica.shared.infraestructura.ConexionBD;
import com.zonaacme.sica.shared.infraestructura.ContextoJdbc;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcRepositorioPersonas implements RepositorioPersonas {

    private static final String SELECT_BASE = """
            SELECT p.id, p.nombre, p.tipo_documento, p.documento_identidad, p.telefono,
                   p.url_foto, p.tipo_persona, p.empresa_id, p.motivo_bloqueo, p.fecha_bloqueo,
                   e.nombre AS empresa_nombre,
                   ea.nombre_estado AS estado_acceso
              FROM personas p
              LEFT JOIN empresas e                ON e.id  = p.empresa_id
              LEFT JOIN persona_estados_acceso ea ON ea.id = p.estado_acceso_id
            """;

    @Override
    public long crear(ContextoTransaccion contexto, ComandoGuardarPersona comando) {
        String sql = """
                INSERT INTO personas
                    (nombre, tipo_documento, documento_identidad, telefono, url_foto,
                     tipo_persona, empresa_id, estado_acceso_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            asignarDatos(sentencia, comando);
            // Toda persona nace con acceso permitido. Bloquear es una accion
            // deliberada que debe quedar auditada, nunca un estado inicial.
            sentencia.setInt(8, CatalogoEstados.idAccesoPermitido());
            sentencia.executeUpdate();

            try (ResultSet clave = sentencia.getGeneratedKeys()) {
                if (!clave.next()) {
                    throw new ExcepcionTecnica("El alta de persona no devolvio identificador.");
                }
                return clave.getLong(1);
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo registrar la persona.", e);
        }
    }

    /**
     * La actualizacion NO toca estado_acceso_id ni motivo_bloqueo.
     *
     * Es deliberado: si editar los datos de contacto pudiera cambiar el estado
     * de acceso, una edicion rutinaria podria levantar una restriccion sin
     * dejar rastro. Cambiar el acceso tiene su propio caso de uso, su propio
     * permiso y su propia entrada en la bitacora.
     */
    @Override
    public void actualizar(ContextoTransaccion contexto, ComandoGuardarPersona comando) {
        String sql = """
                UPDATE personas
                   SET nombre = ?, tipo_documento = ?, documento_identidad = ?,
                       telefono = ?, url_foto = ?, tipo_persona = ?, empresa_id = ?
                 WHERE id = ?
                """;

        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(sql)) {

            asignarDatos(sentencia, comando);
            sentencia.setLong(8, comando.id());
            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudieron actualizar los datos de la persona.", e);
        }
    }

    private void asignarDatos(PreparedStatement sentencia, ComandoGuardarPersona comando)
            throws SQLException {
        sentencia.setString(1, comando.nombre().trim());
        sentencia.setString(2, comando.tipoDocumento());
        sentencia.setString(3, comando.documentoIdentidad().trim());
        sentencia.setString(4, vacioComoNulo(comando.telefono()));
        sentencia.setString(5, vacioComoNulo(comando.urlFoto()));
        sentencia.setString(6, comando.tipo().valorEnBd());
        if (comando.empresaId() == null) {
            sentencia.setNull(7, Types.INTEGER);
        } else {
            sentencia.setLong(7, comando.empresaId());
        }
    }

    @Override
    public void cambiarEstadoAcceso(ContextoTransaccion contexto,
                                    ComandoCambiarEstadoAcceso comando) {
        String sql = """
                UPDATE personas
                   SET estado_acceso_id = ?, motivo_bloqueo = ?, fecha_bloqueo = ?
                 WHERE id = ?
                """;

        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(sql)) {

            sentencia.setInt(1, comando.bloquear()
                    ? CatalogoEstados.idAccesoBloqueado()
                    : CatalogoEstados.idAccesoPermitido());

            // Al desbloquear se conserva el motivo del levantamiento, no se
            // borra: la bitacora guarda el evento y esta columna deja visible
            // en la ficha por que se tomo la ultima decision.
            sentencia.setString(2, comando.motivo().trim());
            sentencia.setTimestamp(3, comando.bloquear()
                    ? Timestamp.valueOf(java.time.LocalDateTime.now())
                    : null);
            sentencia.setLong(4, comando.personaId());
            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo cambiar el estado de acceso.", e);
        }
    }

    @Override
    public Optional<Persona> buscarPorId(ContextoTransaccion contexto, long personaId) {
        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(SELECT_BASE + " WHERE p.id = ?")) {

            sentencia.setLong(1, personaId);
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next() ? Optional.of(mapear(fila)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar la persona.", e);
        }
    }

    @Override
    public boolean documentoOcupado(ContextoTransaccion contexto, String documento, Long exceptoId) {
        String sql = "SELECT 1 FROM personas WHERE documento_identidad = ? AND id <> ? LIMIT 1";

        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(sql)) {

            sentencia.setString(1, documento);
            // En un alta no hay id que excluir; -1 no coincide con ninguna fila.
            sentencia.setLong(2, exceptoId == null ? -1L : exceptoId);
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next();
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo verificar el documento.", e);
        }
    }

    @Override
    public boolean tieneHistorial(ContextoTransaccion contexto, long personaId) {
        String sql = "SELECT 1 FROM visitas WHERE persona_id = ? LIMIT 1";

        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement(sql)) {

            sentencia.setLong(1, personaId);
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next();
            }
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo verificar el historial de la persona.", e);
        }
    }

    @Override
    public void eliminar(ContextoTransaccion contexto, long personaId) {
        try (PreparedStatement sentencia = ContextoJdbc.conexionDe(contexto)
                .prepareStatement("DELETE FROM personas WHERE id = ?")) {

            sentencia.setLong(1, personaId);
            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo eliminar la persona.", e);
        }
    }

    /**
     * Las personas bloqueadas se listan primero. Quien abre esta pantalla suele
     * venir a revisar una restriccion, no a hojear el directorio completo.
     */
    @Override
    public List<Persona> listar(String filtro) {
        boolean hayFiltro = filtro != null && !filtro.isBlank();

        String sql = SELECT_BASE
                + (hayFiltro ? " WHERE p.nombre LIKE ? OR p.documento_identidad LIKE ? " : "")
                + " ORDER BY ea.nombre_estado = '" + EstadoAcceso.BLOQUEADO.valorEnBd() + "' DESC,"
                + "          p.nombre";

        List<Persona> personas = new ArrayList<>();
        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {

            if (hayFiltro) {
                String patron = "%" + filtro.trim() + "%";
                sentencia.setString(1, patron);
                sentencia.setString(2, patron);
            }
            try (ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    personas.add(mapear(fila));
                }
            }
            return personas;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo listar el directorio de personas.", e);
        }
    }

    private Persona mapear(ResultSet fila) throws SQLException {
        Timestamp bloqueo = fila.getTimestamp("fecha_bloqueo");

        // wasNull() se refiere SIEMPRE al ultimo getter invocado, asi que el
        // valor tiene que resolverse aqui y no dentro del constructor: alli,
        // las llamadas intermedias ya habrian cambiado a que se refiere.
        long empresaCruda = fila.getLong("empresa_id");
        Long empresaId = fila.wasNull() ? null : empresaCruda;

        return new Persona(
                fila.getLong("id"),
                fila.getString("nombre"),
                fila.getString("tipo_documento"),
                fila.getString("documento_identidad"),
                fila.getString("telefono"),
                fila.getString("url_foto"),
                TipoPersona.desdeBd(fila.getString("tipo_persona")),
                empresaId,
                fila.getString("empresa_nombre"),
                EstadoAcceso.desdeBd(fila.getString("estado_acceso")),
                fila.getString("motivo_bloqueo"),
                bloqueo == null ? null : bloqueo.toLocalDateTime());
    }

    private String vacioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
