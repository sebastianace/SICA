package com.zonaacme.sica.shared.infraestructura;

import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.aplicacion.OperacionTransaccional;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Adaptador que resuelve el ciclo commit / rollback sobre MySQL.
 *
 * Uso tipico dentro de un caso de uso:
 *
 *   Visita nueva = gestor.ejecutar(contexto -> {
 *       repositorio.cerrarPorSalidaOlvidada(contexto, visitaAbierta.id());
 *       return repositorio.crear(contexto, visitaNueva);
 *   });
 *
 * Las dos escrituras viven o mueren juntas.
 */
public final class GestorTransaccionesJdbc implements GestorTransacciones {

    @Override
    public <T> T ejecutar(OperacionTransaccional<T> operacion) {
        Connection conexion = ConexionBD.abrir();
        try {
            conexion.setAutoCommit(false);

            try {
                T resultado = operacion.aplicar(new ContextoJdbc(conexion));
                conexion.commit();
                return resultado;

            } catch (ExcepcionDominio reglaViolada) {
                // Una regla de negocio tambien revierte, pero se propaga tal cual
                // para que la interfaz muestre el mensaje real al usuario.
                revertir(conexion);
                throw reglaViolada;

            } catch (RuntimeException fallo) {
                revertir(conexion);
                throw fallo;
            }

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudo abrir la transaccion.", e);
        } finally {
            cerrar(conexion);
        }
    }

    private void revertir(Connection conexion) {
        try {
            conexion.rollback();
        } catch (SQLException e) {
            System.err.println("[tx] fallo el rollback: " + e.getMessage());
        }
    }

    private void cerrar(Connection conexion) {
        try {
            conexion.setAutoCommit(true);
            conexion.close();
        } catch (SQLException e) {
            System.err.println("[tx] fallo al cerrar la conexion: " + e.getMessage());
        }
    }
}
