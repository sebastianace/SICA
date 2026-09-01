package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.EstadoVisita;
import com.zonaacme.sica.acceso.dominio.TipoVisita;

import java.time.LocalDateTime;

/** Fila de la bandeja de aprobaciones del funcionario. */
public record SolicitudPendiente(
        long          visitaId,
        long          personaId,
        String        nombrePersona,
        String        documento,
        String        fotoUrl,
        String        empresaDestino,
        TipoVisita    tipo,
        EstadoVisita  estado,
        String        motivo,
        LocalDateTime solicitadaEn
) { }
