package com.zonaacme.sica.directorio.aplicacion;

/** Funcionario que puede autorizar visitas a su empresa. */
public record AnfitrionBreve(long usuarioId, String nombre, String correo, long empresaId) {
    @Override public String toString() { return nombre; }
}
