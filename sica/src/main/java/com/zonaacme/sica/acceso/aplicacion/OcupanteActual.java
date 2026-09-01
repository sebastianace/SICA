package com.zonaacme.sica.acceso.aplicacion;

import java.time.LocalDateTime;

/**
 * Fila del tablero de ocupacion.
 *
 * Responde la pregunta que el enunciado plantea como problema numero uno del
 * sistema viejo: quien esta dentro del complejo en este momento. En una
 * evacuacion, este listado ES el recuento de personal.
 */
public record OcupanteActual(
        long          visitaId,
        long          personaId,
        String        documento,
        String        nombreCompleto,
        String        tipoPersona,
        String        empresaDestino,
        String        torre,
        LocalDateTime fechaIngreso,
        long          minutosDentro
) {
    public String tiempoDentroLegible() {
        long horas = minutosDentro / 60;
        long minutos = minutosDentro % 60;
        return horas > 0 ? horas + "h " + minutos + "m" : minutos + "m";
    }
}
