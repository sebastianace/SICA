package com.zonaacme.sica.directorio.aplicacion;

/** Empresa como aparece en un selector. */
public record EmpresaBreve(long id, String nombre, String torre, String oficina) {
    public String etiqueta() {
        return torre == null ? nombre : nombre + " — " + torre + (oficina == null ? "" : " " + oficina);
    }
    @Override public String toString() { return etiqueta(); }
}
