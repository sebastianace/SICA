package com.zonaacme.sica.incidentes.dominio;

/**
 * Gravedad del incidente.
 *
 * requiereBloqueoInmediato() no es un adorno: conecta el modulo de incidentes
 * con el de personas. Un incidente grave es exactamente el caso que el
 * enunciado describe como "gestion reactiva", y el sistema debe sugerir la
 * prohibicion de ingreso en ese mismo momento, no al dia siguiente por radio.
 */
public enum GravedadIncidente {
    BAJA("Baja"), MEDIA("Media"), ALTA("Alta"), CRITICA("Critica");

    private final String etiqueta;
    GravedadIncidente(String etiqueta) { this.etiqueta = etiqueta; }
    public String etiqueta() { return etiqueta; }

    public boolean requiereBloqueoInmediato() {
        return this == ALTA || this == CRITICA;
    }
}
