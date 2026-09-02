package com.zonaacme.sica.incidentes.dominio;

/** Ciclo de vida del incidente. Un incidente cerrado ya no se reabre. */
public enum EstadoIncidente {
    ABIERTO, EN_REVISION, CERRADO;

    public boolean permiteCierre() { return this != CERRADO; }
}
