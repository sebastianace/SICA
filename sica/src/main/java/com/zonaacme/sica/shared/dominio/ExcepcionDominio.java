package com.zonaacme.sica.shared.dominio;

/**
 * Excepcion base de todas las reglas de negocio violadas.
 * Es no verificada (RuntimeException) a proposito: una regla de negocio rota no
 * es algo que la capa de interfaz deba capturar en cada llamada, sino algo que
 * se muestra al usuario en un solo punto.
 */
public class ExcepcionDominio extends RuntimeException {
    public ExcepcionDominio(String mensaje) { super(mensaje); }
    public ExcepcionDominio(String mensaje, Throwable causa) { super(mensaje, causa); }
}
