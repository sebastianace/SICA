package com.zonaacme.sica.shared.dominio;

/**
 * Envuelve fallos de infraestructura (base de datos, red, archivos) para que el
 * dominio y la aplicacion nunca tengan que importar java.sql.SQLException.
 * Es una de las fronteras que mantiene el nucleo libre de detalles tecnicos.
 */
public class ExcepcionTecnica extends RuntimeException {
    public ExcepcionTecnica(String mensaje, Throwable causa) { super(mensaje, causa); }

    /**
     * Para fallos de configuracion detectados por el propio sistema, donde no
     * hay una excepcion subyacente que envolver: por ejemplo, un catalogo de
     * estados incompleto en la base de datos. Se detectan al arrancar, no
     * cuando un guarda esta atendiendo a alguien en la porteria.
     */
    public ExcepcionTecnica(String mensaje) { super(mensaje); }
}
