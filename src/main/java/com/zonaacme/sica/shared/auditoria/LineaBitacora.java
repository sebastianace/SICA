package com.zonaacme.sica.shared.auditoria;

import java.time.LocalDateTime;

/** Proyeccion de lectura de la bitacora, para mostrarla en pantalla. */
public record LineaBitacora(
        long          id,
        String        usuario,
        String        accion,
        String        entidadAfectada,
        Long          idEntidad,
        String        detalle,
        String        resultado,
        String        terminal,
        LocalDateTime fechaHora,
        String        hashActual
) { }
