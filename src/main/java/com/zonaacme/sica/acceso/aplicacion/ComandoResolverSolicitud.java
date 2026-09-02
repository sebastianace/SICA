package com.zonaacme.sica.acceso.aplicacion;

/**
 * Orden del funcionario sobre una solicitud que espera su visto bueno.
 *
 * Un solo comando para aprobar y rechazar, porque son la misma decision con
 * distinto signo y comparten validaciones: que la visita exista, que siga
 * esperando respuesta y que quien responde sea el anfitrion legitimo.
 */
public record ComandoResolverSolicitud(
        long    visitaId,
        boolean aprobar,
        String  observaciones
) {

    public static ComandoResolverSolicitud aprobar(long visitaId) {
        return new ComandoResolverSolicitud(visitaId, true, null);
    }

    public static ComandoResolverSolicitud rechazar(long visitaId, String motivo) {
        return new ComandoResolverSolicitud(visitaId, false, motivo);
    }
}
