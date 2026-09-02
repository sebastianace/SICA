package com.zonaacme.sica.acceso.aplicacion;

import java.time.LocalDateTime;

/**
 * Resumen minimo de la visita que una persona tiene abierta en este momento.
 *
 * Existe porque la porteria necesita dos datos, no uno: el identificador para
 * poder cerrarla, y la hora de ingreso para poder mostrarle al guarda desde
 * cuando figura adentro esa persona.
 *
 * Ese segundo dato es el que convierte el flujo de salida olvidada en algo
 * comprensible: no es lo mismo "tiene una visita abierta" que "figura adentro
 * desde ayer a las 5:59 p.m.". El guarda toma la decision con el segundo.
 */
public record VisitaAbiertaResumen(
        long          visitaId,
        LocalDateTime fechaIngreso
) { }
