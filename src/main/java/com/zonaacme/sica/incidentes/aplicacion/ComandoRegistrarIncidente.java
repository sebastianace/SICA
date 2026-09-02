package com.zonaacme.sica.incidentes.aplicacion;

import com.zonaacme.sica.incidentes.dominio.GravedadIncidente;
import com.zonaacme.sica.incidentes.dominio.TipoIncidente;

/**
 * Orden de reportar un incidente.
 *
 * personaId y visitaId son opcionales porque no todo incidente involucra a una
 * persona identificada ni ocurre durante una visita: alguien que intenta entrar
 * con un documento que no es suyo no tiene todavia una visita asociada.
 */
public record ComandoRegistrarIncidente(
        Long              personaId,
        Long              visitaId,
        TipoIncidente     tipo,
        GravedadIncidente gravedad,
        String            descripcion
) {
    public ComandoRegistrarIncidente {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion del incidente es obligatoria.");
        }
        if (tipo == null || gravedad == null) {
            throw new IllegalArgumentException("El tipo y la gravedad son obligatorios.");
        }
    }
}
