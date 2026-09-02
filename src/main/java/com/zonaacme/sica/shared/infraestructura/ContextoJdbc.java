package com.zonaacme.sica.shared.infraestructura;

import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;

import java.sql.Connection;

/**
 * Implementacion JDBC del contexto transaccional: por dentro es una Connection.
 * Los repositorios la desempacan con {@link #conexionDe}. Es el unico punto del
 * sistema donde la abstraccion se convierte de nuevo en algo concreto.
 */
public final class ContextoJdbc implements ContextoTransaccion {

    private final Connection conexion;

    ContextoJdbc(Connection conexion) {
        this.conexion = conexion;
    }

    public static Connection conexionDe(ContextoTransaccion contexto) {
        if (!(contexto instanceof ContextoJdbc contextoJdbc)) {
            throw new ExcepcionTecnica(
                    "Se esperaba un contexto transaccional JDBC y llego "
                  + (contexto == null ? "null" : contexto.getClass().getName()), null);
        }
        return contextoJdbc.conexion;
    }
}
