package com.zonaacme.sica.shared.aplicacion;

/**
 * Lo que un caso de uso le cuenta a la bitacora sobre lo que acaba de hacer:
 * sobre que registro actuo y una descripcion legible por un humano.
 */
public record DescripcionAuditoria(Long idEntidad, String detalle) {

    public static DescripcionAuditoria de(Long idEntidad, String detalle) {
        return new DescripcionAuditoria(idEntidad, detalle);
    }

    public static DescripcionAuditoria soloDetalle(String detalle) {
        return new DescripcionAuditoria(null, detalle);
    }
}
