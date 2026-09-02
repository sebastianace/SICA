package com.zonaacme.sica.shared.aplicacion;

/**
 * Unidad de trabajo atomica. Al ser interfaz funcional, los casos de uso la
 * escriben como lambda y leen como un bloque de negocio, no como plomeria.
 */
@FunctionalInterface
public interface OperacionTransaccional<T> {
    T aplicar(ContextoTransaccion contexto);
}
