package com.zonaacme.sica.personas.aplicacion;

import com.zonaacme.sica.personas.dominio.TipoPersona;

/**
 * Alta y edicion en un solo comando: cuando id es null es un alta y cuando
 * trae valor es una edicion. Son la misma operacion con las mismas
 * validaciones; separarlas en dos comandos duplicaria las reglas.
 */
public record ComandoGuardarPersona(
        Long        id,
        String      nombre,
        String      tipoDocumento,
        String      documentoIdentidad,
        String      telefono,
        String      urlFoto,
        TipoPersona tipo,
        Long        empresaId
) {
    public ComandoGuardarPersona {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        if (documentoIdentidad == null || documentoIdentidad.isBlank()) {
            throw new IllegalArgumentException("El documento es obligatorio.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de persona es obligatorio.");
        }
        // Un trabajador sin empresa no se le puede dirigir a ninguna parte en
        // la porteria, y su rutina de ingreso no tendria destino.
        if (tipo.exigeEmpresa() && empresaId == null) {
            throw new IllegalArgumentException("Un trabajador debe estar asociado a una empresa.");
        }
    }

    public boolean esAlta() { return id == null; }
}
