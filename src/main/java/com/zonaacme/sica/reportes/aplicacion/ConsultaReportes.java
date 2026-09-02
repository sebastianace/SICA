package com.zonaacme.sica.reportes.aplicacion;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto de lectura para los reportes.
 *
 * Es un puerto SEPARADO de RepositorioVisitas, y no un metodo mas dentro de el,
 * por segregacion de interfaces: quien genera reportes no necesita poder crear
 * ni modificar visitas. Ademas los reportes leen filas aplanadas con joins ya
 * resueltos, que es una forma distinta de los datos a la que usa el dominio.
 */
public interface ConsultaReportes {

    /**
     * Visitas del rango, con las fechas inclusivas en ambos extremos.
     * empresaId null significa todo el complejo.
     */
    List<FilaVisita> visitasEntre(LocalDate desde, LocalDate hasta, Long empresaId);
}
