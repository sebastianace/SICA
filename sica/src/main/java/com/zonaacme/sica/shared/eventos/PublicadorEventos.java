package com.zonaacme.sica.shared.eventos;

import com.zonaacme.sica.shared.dominio.EventoDominio;
import java.util.function.Consumer;

/**
 * Puerto de salida para la notificacion en tiempo real.
 *
 * El dominio solo conoce esta interfaz. Hoy el adaptador es un bus en memoria,
 * porque SICA corre como una sola aplicacion de escritorio. Si manana el
 * sistema opera en tres porterias distintas, se escribe un adaptador de sockets
 * o de sondeo a base de datos y el nucleo no cambia ni una linea.
 * Esa es exactamente la razon de ser de la arquitectura hexagonal.
 */
public interface PublicadorEventos {

    void publicar(EventoDominio evento);

    <T extends EventoDominio> void suscribir(Class<T> tipoEvento, Consumer<T> manejador);
}
