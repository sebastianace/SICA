package com.zonaacme.sica.shared.aplicacion;

/**
 * Puerto de salida para el control transaccional.
 *
 * Es imprescindible en el flujo de salida olvidada: cerrar la visita anterior y
 * abrir la nueva deben ser una sola operacion atomica. Si el proceso se cayera
 * entre las dos, la persona quedaria con dos visitas en estado DENTRO y se
 * romperia la invariante central del sistema.
 */
public interface GestorTransacciones {
    <T> T ejecutar(OperacionTransaccional<T> operacion);
}
