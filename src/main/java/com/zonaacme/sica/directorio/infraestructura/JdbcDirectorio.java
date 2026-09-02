package com.zonaacme.sica.directorio.infraestructura;

import com.zonaacme.sica.directorio.aplicacion.AnfitrionBreve;
import com.zonaacme.sica.directorio.aplicacion.Directorio;
import com.zonaacme.sica.directorio.aplicacion.EmpresaBreve;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;
import com.zonaacme.sica.shared.infraestructura.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcDirectorio implements Directorio {

    private static final String SQL_EMPRESAS = """
            SELECT id, nombre, torre, oficina
              FROM empresas
             WHERE esta_activa = TRUE
             ORDER BY nombre
            """;

    /**
     * Un anfitrion valido es un usuario activo de esa empresa que ademas TIENE
     * el permiso de aprobar visitas.
     *
     * El filtro por permiso, y no por nombre de rol, es deliberado: si manana
     * se crea un rol nuevo con esa facultad, aparece aqui sin tocar el codigo.
     * Filtrar por rol_id = 3 seria volver a meter el RBAC dentro del programa.
     */
    private static final String SQL_ANFITRIONES = """
            SELECT u.id, u.nombre, u.email, u.empresa_id
              FROM usuarios u
              JOIN rol_permisos rp ON rp.rol_id = u.rol_id
              JOIN permisos p      ON p.id = rp.permiso_id
             WHERE u.empresa_id = ?
               AND u.esta_activo = TRUE
               AND p.nombre_permiso = 'aprobar_visita'
             ORDER BY u.nombre
            """;

    @Override
    public List<EmpresaBreve> empresasActivas() {
        List<EmpresaBreve> empresas = new ArrayList<>();
        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_EMPRESAS);
             ResultSet fila = sentencia.executeQuery()) {

            while (fila.next()) {
                empresas.add(new EmpresaBreve(
                        fila.getLong("id"),
                        fila.getString("nombre"),
                        fila.getString("torre"),
                        fila.getString("oficina")));
            }
            return empresas;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo consultar el directorio de empresas.", e);
        }
    }

    @Override
    public List<AnfitrionBreve> anfitrionesDe(long empresaId) {
        List<AnfitrionBreve> anfitriones = new ArrayList<>();
        try (Connection conexion = ConexionBD.abrir();
             PreparedStatement sentencia = conexion.prepareStatement(SQL_ANFITRIONES)) {

            sentencia.setLong(1, empresaId);
            try (ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    anfitriones.add(new AnfitrionBreve(
                            fila.getLong("id"),
                            fila.getString("nombre"),
                            fila.getString("email"),
                            fila.getLong("empresa_id")));
                }
            }
            return anfitriones;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudieron consultar los anfitriones.", e);
        }
    }
}
