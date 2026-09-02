package com.zonaacme.sica.reportes.aplicacion;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Una visita, aplanada, tal como sale de la base de datos para los reportes.
 *
 * Es deliberadamente un registro de DETALLE y no un total ya calculado.
 *
 * La razon: sobre este mismo conjunto de filas se construyen varios cortes
 * distintos (por empresa, por tipo de visita, por dia, por duracion). Si cada
 * corte se resolviera con su propio GROUP BY, una sola pantalla de reportes
 * costaria cuatro o cinco viajes a la base de datos sobre exactamente los
 * mismos datos. Se trae el detalle una vez, filtrado e indexado por SQL, que es
 * lo que SQL hace bien, y las agregaciones se arman en memoria con Stream API.
 *
 * El limite de este enfoque es la memoria: con millones de visitas habria que
 * volver a empujar las agregaciones a SQL o pasar a vistas materializadas. Para
 * un complejo de 30 empresas, el detalle de un mes cabe holgadamente.
 */
public record FilaVisita(
        long          visitaId,
        long          personaId,
        String        nombrePersona,
        String        documento,
        String        tipoPersona,
        String        empresa,
        String        torre,
        String        tipoVisita,
        String        estado,
        LocalDateTime fechaEntrada,
        LocalDateTime fechaSalida,
        String        registradaPor
) {

    /** True si la visita llego a materializarse en un ingreso real. */
    public boolean tuvoIngreso() {
        return fechaEntrada != null;
    }

    public boolean estaDentro() {
        return "Dentro".equals(estado);
    }

    /** Una visita que el sistema tuvo que cerrar porque nadie registro la salida. */
    public boolean fueCerradaPorSistema() {
        return "Cerrado por Sistema".equals(estado);
    }

    /**
     * Duracion de la estancia. Vacia si la visita no ha terminado o nunca
     * empezo: un promedio calculado sobre visitas abiertas mentiria, porque
     * cada minuto que pasa lo cambiaria.
     */
    public Optional<Long> minutosDeEstancia() {
        if (fechaEntrada == null || fechaSalida == null) {
            return Optional.empty();
        }
        return Optional.of(ChronoUnit.MINUTES.between(fechaEntrada, fechaSalida));
    }

    /** Hora del dia del ingreso, para el corte de horas pico. */
    public Optional<Integer> horaDeIngreso() {
        return Optional.ofNullable(fechaEntrada).map(LocalDateTime::getHour);
    }
}
