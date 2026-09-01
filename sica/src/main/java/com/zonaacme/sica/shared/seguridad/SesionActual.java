package com.zonaacme.sica.shared.seguridad;

import com.zonaacme.sica.shared.dominio.ExcepcionDominio;

/**
 * Contenedor del usuario en sesion.
 *
 * NO es estatico a proposito. En la demostracion se abren dos ventanas en la
 * misma JVM (un guarda y un funcionario) y cada una necesita su propia sesion.
 * Una variable estatica compartida haria que el segundo login pisara al primero.
 */
public final class SesionActual {

    private volatile UsuarioAutenticado usuario;
    private final String terminal;

    public SesionActual(String terminal) {
        this.terminal = terminal;
    }

    public void iniciar(UsuarioAutenticado usuario) { this.usuario = usuario; }

    public void cerrar() { this.usuario = null; }

    public boolean haySesion() { return usuario != null; }

    public UsuarioAutenticado usuario() { return usuario; }

    public UsuarioAutenticado obligatorio() {
        if (usuario == null) {
            throw new ExcepcionDominio("No hay una sesion abierta.");
        }
        return usuario;
    }

    public String terminal() { return terminal; }
}
