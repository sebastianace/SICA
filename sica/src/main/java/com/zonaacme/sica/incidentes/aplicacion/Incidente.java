package com.zonaacme.sica.incidentes.aplicacion;

import com.zonaacme.sica.incidentes.dominio.EstadoIncidente;
import com.zonaacme.sica.incidentes.dominio.GravedadIncidente;
import com.zonaacme.sica.incidentes.dominio.TipoIncidente;

import java.time.LocalDateTime;

/** Un incidente tal como se lista en pantalla, con los joins ya resueltos. */
public record Incidente(
        long              id,
        Long              visitaId,
        Long              personaId,
        String            nombrePersona,
        String            documentoPersona,
        String            reportadoPor,
        LocalDateTime     fecha,
        String            descripcion,
        TipoIncidente     tipo,
        GravedadIncidente gravedad,
        EstadoIncidente   estado
) {
    public boolean estaAbierto() { return estado != EstadoIncidente.CERRADO; }
}
