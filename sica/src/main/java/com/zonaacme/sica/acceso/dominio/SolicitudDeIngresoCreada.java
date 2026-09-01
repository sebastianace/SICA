package com.zonaacme.sica.acceso.dominio;

import com.zonaacme.sica.shared.dominio.EventoDominio;

import java.time.LocalDateTime;

/**
 * Se publica cuando el guarda registra un ingreso que necesita el visto bueno
 * del anfitrion (flujos 2 y 3). La ventana del funcionario esta suscrita a este
 * evento y hace aparecer la solicitud sin que nadie refresque nada.
 *
 * Se publica DESPUES del commit: notificar algo que luego se revierte seria
 * peor que no notificar.
 */
public record SolicitudDeIngresoCreada(
        long          visitaId,
        long          personaId,
        String        nombrePersona,
        String        documento,
        String        fotoUrl,
        long          empresaDestinoId,
        Long          anfitrionUsuarioId,
        TipoVisita    tipo,
        String        motivo,
        LocalDateTime ocurridoEn
) implements EventoDominio {

    public static SolicitudDeIngresoCreada de(Visita visita, PersonaEnPorteria persona) {
        return new SolicitudDeIngresoCreada(
                visita.id(), persona.id(), persona.nombreCompleto(),
                persona.documentoFormateado(), persona.fotoUrl(),
                visita.empresaDestinoId(), visita.anfitrionUsuarioId(),
                visita.tipo(), visita.motivo(), LocalDateTime.now());
    }
}
