package com.zonaacme.sica.acceso.dominio;

import com.zonaacme.sica.shared.dominio.EventoDominio;

import java.time.LocalDateTime;

/**
 * Se publica cuando el funcionario aprueba o rechaza. La pantalla del guarda
 * esta suscrita y se actualiza sola, que es exactamente lo que pide el
 * enunciado en el flujo del invitado no anunciado.
 */
public record SolicitudDeIngresoResuelta(
        long          visitaId,
        long          personaId,
        String        nombrePersona,
        EstadoVisita  estadoFinal,
        String        resueltaPor,
        String        observaciones,
        LocalDateTime ocurridoEn
) implements EventoDominio {

    public boolean fueAprobada() {
        return estadoFinal == EstadoVisita.APROBADA;
    }
}
