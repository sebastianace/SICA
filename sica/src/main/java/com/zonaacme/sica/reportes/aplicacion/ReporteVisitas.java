package com.zonaacme.sica.reportes.aplicacion;

import java.util.List;
import java.util.Map;

/**
 * Resultado del reporte de visitas: varios cortes calculados sobre un mismo
 * conjunto de filas.
 *
 * Todos los mapas se entregan ORDENADOS. Un reporte cuyo orden cambia entre
 * ejecuciones es inutil para comparar dos corridas, y HashMap no garantiza
 * orden alguno.
 */
public record ReporteVisitas(

        /** Filas de detalle, por si el usuario quiere exportarlas completas. */
        List<FilaVisita> detalle,

        int totalVisitas,
        int totalConIngreso,
        int totalDentroAhora,
        int totalCerradasPorSistema,

        /** Empresa -> cuantas visitas recibio. De mayor a menor. */
        Map<String, Long> visitasPorEmpresa,

        /** Tipo de visita -> cuantas. Muestra el peso de cada flujo del enunciado. */
        Map<String, Long> visitasPorTipo,

        /** Estado -> cuantas. Deja ver si hay muchas rechazadas o expiradas. */
        Map<String, Long> visitasPorEstado,

        /** Hora del dia (0-23) -> ingresos. Identifica las horas pico de la porteria. */
        Map<Integer, Long> ingresosPorHora,

        /** Empresa -> duracion promedio en minutos de las visitas ya cerradas. */
        Map<String, Double> promedioEstanciaPorEmpresa,

        /** Las visitas mas largas ya cerradas, para revision manual. */
        List<FilaVisita> estanciasMasLargas
) {

    /**
     * Proporcion de visitas que el sistema tuvo que cerrar por salida olvidada.
     *
     * Es el indicador mas util del reporte: mide un problema de proceso, no de
     * software. Si sube, significa que la gente esta saliendo sin registrar
     * salida, y el tablero de ocupacion pierde fiabilidad justo cuando mas
     * importa, que es en una evacuacion.
     */
    public double porcentajeSalidasOlvidadas() {
        return totalConIngreso == 0
                ? 0.0
                : (totalCerradasPorSistema * 100.0) / totalConIngreso;
    }

    public boolean estaVacio() {
        return totalVisitas == 0;
    }
}
