package com.zonaacme.sica.acceso.dominio;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Todo lo que la pantalla del guarda muestra tras una busqueda: quien es, si
 * puede entrar y por que.
 *
 * El campo salidaOlvidadaDetectada no bloquea nada. El enunciado es explicito:
 * una salida sin registrar no impide el ingreso, solo debe quedar constancia.
 * Aqui se usa para avisarle al guarda de que el sistema va a cerrar la visita
 * anterior antes de abrir la nueva.
 */
public record FichaPorteria(
        PersonaEnPorteria persona,
        Veredicto         veredicto,
        String            detalle,
        Long              visitaAprobadaId,
        Long              visitaAbiertaId,
        LocalDateTime     ingresoDeVisitaAbierta,
        Long              empresaSugeridaId,
        String            empresaSugeridaNombre,
        Long              anfitrionSugeridoId
) {

    public static FichaPorteria noRegistrado(String tipoDocumento, String numeroDocumento) {
        return new FichaPorteria(null, Veredicto.NO_REGISTRADO,
                "No hay ninguna persona con documento " + tipoDocumento + " " + numeroDocumento
              + " en el sistema.",
                null, null, null, null, null, null);
    }

    public boolean salidaOlvidadaDetectada() {
        return visitaAbiertaId != null;
    }

    public boolean hayPersona() {
        return persona != null;
    }

    public Optional<Long> visitaAprobada() {
        return Optional.ofNullable(visitaAprobadaId);
    }
}
