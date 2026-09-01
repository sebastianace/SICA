package com.zonaacme.sica.shared.dominio;

/** Se lanza cuando el rol del usuario en sesion no tiene el permiso requerido. */
public class ExcepcionPermisoDenegado extends ExcepcionDominio {

    private final String permisoRequerido;

    public ExcepcionPermisoDenegado(String permisoRequerido) {
        super("Tu rol no tiene el permiso '" + permisoRequerido + "'. "
            + "Pide al administrador que lo asigne.");
        this.permisoRequerido = permisoRequerido;
    }

    public String permisoRequerido() { return permisoRequerido; }
}
