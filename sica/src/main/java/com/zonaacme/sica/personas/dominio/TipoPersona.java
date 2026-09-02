package com.zonaacme.sica.personas.dominio;

/**
 * Los valores coinciden con el ENUM de la tabla del modelo entregado.
 *
 * Solo hay dos: un contratista se registra como Invitado, porque esa es su
 * condicion real de acceso: entra por autorizacion puntual y no tiene carnet
 * permanente. Inventar una tercera categoria que la base de datos no acepta
 * habria sido cambiar el modelo por comodidad.
 */
public enum TipoPersona {
    TRABAJADOR("Trabajador"),
    INVITADO("Invitado");

    private final String valorEnBd;
    TipoPersona(String valorEnBd) { this.valorEnBd = valorEnBd; }

    public String valorEnBd() { return valorEnBd; }

    /** Un trabajador tiene empresa fija; un invitado no necesariamente. */
    public boolean exigeEmpresa() { return this == TRABAJADOR; }

    public static TipoPersona desdeBd(String valor) {
        for (TipoPersona tipo : values()) {
            if (tipo.valorEnBd.equalsIgnoreCase(valor)) {
                return tipo;
            }
        }
        return INVITADO;
    }

    @Override public String toString() { return valorEnBd; }
}
