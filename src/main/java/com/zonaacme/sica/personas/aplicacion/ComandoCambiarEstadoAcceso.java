package com.zonaacme.sica.personas.aplicacion;

/**
 * Impone o levanta una prohibicion de ingreso.
 *
 * El motivo es obligatorio en ambos sentidos, no solo al bloquear. Levantar una
 * restriccion sin dejar constancia de por que es exactamente el agujero de
 * trazabilidad que el enunciado quiere cerrar: alguien podria desbloquear a una
 * persona y no habria forma de saber quien lo decidio ni con que criterio.
 */
public record ComandoCambiarEstadoAcceso(long personaId, boolean bloquear, String motivo) {

    public ComandoCambiarEstadoAcceso {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException(
                    bloquear ? "Hay que indicar el motivo del bloqueo."
                             : "Hay que indicar por que se levanta la restriccion.");
        }
    }

    public static ComandoCambiarEstadoAcceso bloquear(long personaId, String motivo) {
        return new ComandoCambiarEstadoAcceso(personaId, true, motivo);
    }

    public static ComandoCambiarEstadoAcceso desbloquear(long personaId, String motivo) {
        return new ComandoCambiarEstadoAcceso(personaId, false, motivo);
    }
}
