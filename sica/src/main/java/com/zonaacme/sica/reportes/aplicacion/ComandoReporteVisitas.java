package com.zonaacme.sica.reportes.aplicacion;

import java.time.LocalDate;

/**
 * Rango del reporte de visitas.
 *
 * empresaId es opcional: cuando viene null el reporte cubre todo el complejo,
 * y cuando trae valor se limita a una empresa. Es el mismo caso de uso, no dos:
 * un supervisor mira todo el complejo, un funcionario solo lo suyo.
 */
public record ComandoReporteVisitas(LocalDate desde, LocalDate hasta, Long empresaId) {

    public ComandoReporteVisitas {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("El rango de fechas es obligatorio.");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
    }

    /** Todo el complejo en un rango. */
    public static ComandoReporteVisitas deTodoElComplejo(LocalDate desde, LocalDate hasta) {
        return new ComandoReporteVisitas(desde, hasta, null);
    }

    /** Los ultimos N dias, incluido hoy. Es el caso de uso mas frecuente. */
    public static ComandoReporteVisitas ultimosDias(int dias) {
        LocalDate hoy = LocalDate.now();
        return new ComandoReporteVisitas(hoy.minusDays(dias - 1L), hoy, null);
    }
}
