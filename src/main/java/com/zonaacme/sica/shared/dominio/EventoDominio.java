package com.zonaacme.sica.shared.dominio;

import java.time.LocalDateTime;

/**
 * Marca de todo hecho relevante ocurrido en el dominio.
 * Los eventos se publican DESPUES del commit: notificar algo que luego se
 * revierte es peor que no notificar nada.
 */
public interface EventoDominio {
    LocalDateTime ocurridoEn();
}
