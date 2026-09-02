package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.EstadoVisita;

public record ResultadoResolucion(
        long         visitaId,
        long         personaId,
        EstadoVisita estado,
        String       nombrePersona,
        boolean      aprobada,
        String       mensaje
) { }
