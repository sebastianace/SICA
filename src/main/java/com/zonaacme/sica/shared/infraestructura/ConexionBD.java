package com.zonaacme.sica.shared.infraestructura;

import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fabrica de conexiones JDBC.
 *
 * DEVUELVE UNA CONEXION NUEVA EN CADA LLAMADA, a proposito.
 *
 * Una unica Connection estatica compartida es el error clasico en proyectos
 * como este y con hilos falla de forma intermitente: java.sql.Connection no es
 * segura para uso concurrente. Como SICA notifica en tiempo real desde un pool
 * de hilos, dos operaciones pueden coincidir en el tiempo y una conexion
 * compartida mezclaria sus transacciones.
 *
 * Cada llamador es responsable de cerrarla, siempre con try-with-resources.
 */
public final class ConexionBD {

    private ConexionBD() { }

    public static Connection abrir() {
        try {
            return DriverManager.getConnection(
                    ConfiguracionApp.urlBaseDatos(),
                    ConfiguracionApp.usuarioBaseDatos(),
                    ConfiguracionApp.claveBaseDatos());
        } catch (SQLException e) {
            throw new ExcepcionTecnica(
                    "No se pudo conectar a MySQL. Revisa que el servicio este arriba "
                  + "y que config.properties tenga la URL y las credenciales correctas.", e);
        }
    }

    /**
     * Prueba de conexion usada al arrancar, para fallar temprano y con un
     * mensaje claro. Aprovecha para cargar los catalogos de estados: si a la
     * base de datos le falta un estado, es mejor saberlo aqui que cuando un
     * guarda tenga a alguien esperando en la porteria.
     */
    public static void verificarConexion() {
        try (Connection conexion = abrir()) {
            if (!conexion.isValid(5)) {
                throw new ExcepcionTecnica("La conexion a MySQL no responde.");
            }
            CatalogoEstados.cargar(conexion);
        } catch (SQLException e) {
            throw new ExcepcionTecnica("Fallo la verificacion de conexion a MySQL.", e);
        }
    }
}
