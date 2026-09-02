package com.zonaacme.sica.usuarios.infraestructura;

import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;
import com.zonaacme.sica.shared.infraestructura.ConexionBD;
import com.zonaacme.sica.usuarios.aplicacion.CredencialUsuario;
import com.zonaacme.sica.usuarios.aplicacion.RepositorioUsuarios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Adaptador de persistencia de usuarios.
 *
 * Todas las sentencias son PreparedStatement, nunca concatenacion de cadenas.
 * No es un capricho de estilo: es lo que impide la inyeccion de SQL. Un login
 * armado con concatenacion se salta escribiendo  ' OR '1'='1  en el usuario.
 */
public final class JdbcRepositorioUsuarios implements RepositorioUsuarios {

    private static final String SQL_CREDENCIAL = """
            SELECT u.id, u.email, u.password, u.nombre,
                   u.empresa_id, u.esta_activo, r.nombre_rol AS rol
              FROM usuarios u
              JOIN roles r ON r.id = u.rol_id
             WHERE u.email = ?
            """;

    /**
     * Los permisos del usuario salen de la base de datos, no de una constante
     * del codigo. Esta consulta es la que hace realidad el requisito de que el
     * RBAC sea configurable sin recompilar.
     */
    private static final String SQL_PERMISOS = """
            SELECT p.nombre_permiso
              FROM usuarios u
              JOIN rol_permisos rp ON rp.rol_id = u.rol_id
              JOIN permisos p      ON p.id = rp.permiso_id
             WHERE u.id = ?
            """;

    @Override
    public Optional<CredencialUsuario> buscarPorUsername(String username) {
        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_CREDENCIAL)) {

            sentencia.setString(1, username);

            try (ResultSet fila = sentencia.executeQuery()) {
                if (!fila.next()) {
                    return Optional.empty();
                }
                long id = fila.getLong("id");
                return Optional.of(new CredencialUsuario(
                        id,
                        fila.getString("email"),
                        fila.getString("password"),
                        fila.getString("nombre"),
                        fila.getString("rol"),
                        idOpcional(fila, "empresa_id"),
                        fila.getBoolean("esta_activo"),
                        permisosDe(conexion, id)));
            }

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar el usuario.", e);
        }
    }

    /**
     * El modelo usa INT para las claves; el dominio trabaja con Long.
     * getObject devolveria Integer y el cast a Long fallaria en ejecucion.
     */
    private Long idOpcional(ResultSet fila, String columna) throws SQLException {
        long valor = fila.getLong(columna);
        return fila.wasNull() ? null : valor;
    }

    private Set<String> permisosDe(Connection conexion, long usuarioId) throws SQLException {
        Set<String> permisos = new HashSet<>();
        try (PreparedStatement sentencia = conexion.prepareStatement(SQL_PERMISOS)) {
            sentencia.setLong(1, usuarioId);
            try (ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    permisos.add(fila.getString("nombre_permiso"));
                }
            }
        }
        return permisos;
    }

    @Override
    public void registrarAccesoExitoso(long usuarioId) {
        ejecutarActualizacion(
                "UPDATE usuarios SET ultimo_acceso = NOW() WHERE id = ?",
                sentencia -> sentencia.setLong(1, usuarioId));
    }

    /**
     * El modelo de datos entregado no tiene un contador de intentos fallidos en
     * la tabla usuarios. No hace falta: el enunciado pide que los intentos
     * fallidos queden en la bitacora de auditoria, y alli quedan, con el correo
     * que se intento usar en la columna usuario_intento.
     *
     * Contar los fallos en la fila del usuario ademas tiene un problema: si el
     * correo no existe, no hay fila que actualizar, y esos son justamente los
     * intentos mas interesantes de auditar.
     */
    @Override
    public void registrarIntentoFallido(String username) {
        // Intencionalmente sin operacion. La constancia la deja la bitacora.
    }

    @FunctionalInterface
    private interface Parametrizador {
        void aplicar(PreparedStatement sentencia) throws SQLException;
    }

    private void ejecutarActualizacion(String sql, Parametrizador parametros) {
        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            parametros.aplicar(sentencia);
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo actualizar el usuario.", e);
        }
    }
}
