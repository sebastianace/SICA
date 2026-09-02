package com.zonaacme.sica.incidentes.aplicacion;

import com.zonaacme.sica.incidentes.dominio.GravedadIncidente;

/**
 * sugiereBloqueo le dice a la interfaz que ofrezca imponer la prohibicion de
 * ingreso. La decision la toma el dominio (GravedadIncidente), no la pantalla:
 * si manana cambia el criterio, cambia en un solo lugar.
 */
public record ResultadoIncidente(
        long              incidenteId,
        GravedadIncidente gravedad,
        boolean           sugiereBloqueo,
        String            mensaje
) { }
