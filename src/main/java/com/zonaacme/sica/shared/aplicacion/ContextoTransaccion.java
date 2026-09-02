package com.zonaacme.sica.shared.aplicacion;

/**
 * Testigo de que se esta trabajando dentro de una transaccion abierta.
 *
 * Es una interfaz vacia a proposito. Su unico trabajo es viajar por las firmas
 * de los puertos de repositorio para que varias operaciones compartan la misma
 * transaccion, SIN que la capa de aplicacion tenga que conocer java.sql.Connection.
 *
 * Sin esta abstraccion, cada puerto tendria que declarar
 *      Optional&lt;Visita&gt; buscarAbierta(Connection conexion, long personaId)
 * y el nucleo hexagonal quedaria amarrado a JDBC. Con ella, el nucleo solo dice
 * "esto ocurre dentro de una transaccion" y quien sabe que es una conexion de
 * MySQL es unicamente el adaptador.
 */
public interface ContextoTransaccion {
}
