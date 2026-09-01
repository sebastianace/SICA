package com.zonaacme.sica.acceso.dominio;

/** Los cuatro caminos por los que alguien puede aparecer en la porteria. */
public enum TipoVisita {

    PRE_REGISTRADA   ("Invitado pre-registrado"),
    NO_ANUNCIADA     ("Invitado no anunciado"),
    OLVIDO_CARNET    ("Trabajador sin carnet"),
    RUTINA_TRABAJADOR("Trabajador en jornada");

    private final String etiqueta;

    TipoVisita(String etiqueta) { this.etiqueta = etiqueta; }

    /**
     * El estado con el que nace una visita depende del camino por el que llego.
     * Concentrar esta decision aqui evita que cada servicio la repita.
     */
    public EstadoVisita estadoInicial() {
        return switch (this) {
            case PRE_REGISTRADA    -> EstadoVisita.APROBADA;
            case NO_ANUNCIADA      -> EstadoVisita.PENDIENTE_APROBACION;
            case OLVIDO_CARNET     -> EstadoVisita.PENDIENTE_APROBACION_OLVIDO;
            case RUTINA_TRABAJADOR -> EstadoVisita.DENTRO;
        };
    }

    public String etiqueta() { return etiqueta; }
}
