package com.zonaacme.sica.incidentes.aplicacion;

public record ComandoCerrarIncidente(long incidenteId, String conclusion) {
    public ComandoCerrarIncidente {
        if (conclusion == null || conclusion.isBlank()) {
            throw new IllegalArgumentException(
                    "Para cerrar un incidente hay que escribir la conclusion.");
        }
    }
}
