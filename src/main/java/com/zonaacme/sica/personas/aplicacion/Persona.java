package com.zonaacme.sica.personas.aplicacion;

import com.zonaacme.sica.personas.dominio.EstadoAcceso;
import com.zonaacme.sica.personas.dominio.TipoPersona;

import java.time.LocalDateTime;

/** Una persona tal como se lista y se edita en administracion. */
public record Persona(
        long          id,
        String        nombre,
        String        tipoDocumento,
        String        documentoIdentidad,
        String        telefono,
        String        urlFoto,
        TipoPersona   tipo,
        Long          empresaId,
        String        empresaNombre,
        EstadoAcceso  estadoAcceso,
        String        motivoBloqueo,
        LocalDateTime fechaBloqueo
) {
    public boolean estaBloqueada() { return estadoAcceso == EstadoAcceso.BLOQUEADO; }

    public String documentoFormateado() { return tipoDocumento + " " + documentoIdentidad; }
}
