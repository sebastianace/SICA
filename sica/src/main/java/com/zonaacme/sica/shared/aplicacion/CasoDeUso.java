package com.zonaacme.sica.shared.aplicacion;

/**
 * Puerto de entrada generico. Toda operacion de negocio del sistema implementa
 * esta interfaz: recibe un objeto de entrada y devuelve uno de salida.
 *
 * Que TODOS los casos de uso compartan esta forma es lo que permite envolverlos
 * con decoradores de seguridad y auditoria sin escribir un decorador por cada
 * operacion. Es Interface Segregation llevado al extremo util: una sola
 * responsabilidad, un solo metodo.
 *
 * @param <E> tipo de la entrada (comando)
 * @param <S> tipo de la salida (resultado)
 */
@FunctionalInterface
public interface CasoDeUso<E, S> {
    S ejecutar(E entrada);
}
