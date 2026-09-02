package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.TipoVisita;

/**
 * Orden de ingreso que envia la porteria.
 *
 * visitaPreAprobadaId viene lleno cuando el guarda esta haciendo check-in de una
 * visita que ya existia y estaba aprobada (flujo 1). Viene en null cuando hay
 * que crear una visita nueva (flujos 2, 3 y rutina de trabajador).
 */
public record ComandoRegistrarIngreso(
        String     tipoDocumento,
        String     numeroDocumento,
        TipoVisita tipo,
        Long       empresaDestinoId,
        Long       anfitrionUsuarioId,
        String     motivo,
        Long       visitaPreAprobadaId
) {

    public static ComandoRegistrarIngreso checkInDeVisitaAprobada(String tipoDocumento,
                                                                  String numeroDocumento,
                                                                  long visitaId) {
        return new ComandoRegistrarIngreso(tipoDocumento, numeroDocumento,
                TipoVisita.PRE_REGISTRADA, null, null, null, visitaId);
    }

    public static ComandoRegistrarIngreso rutinaDeTrabajador(String tipoDocumento,
                                                             String numeroDocumento,
                                                             long empresaId) {
        return new ComandoRegistrarIngreso(tipoDocumento, numeroDocumento,
                TipoVisita.RUTINA_TRABAJADOR, empresaId, null, "Jornada laboral", null);
    }

    public static ComandoRegistrarIngreso invitadoNoAnunciado(String tipoDocumento,
                                                              String numeroDocumento,
                                                              long empresaId,
                                                              long anfitrionId,
                                                              String motivo) {
        return new ComandoRegistrarIngreso(tipoDocumento, numeroDocumento,
                TipoVisita.NO_ANUNCIADA, empresaId, anfitrionId, motivo, null);
    }

    public static ComandoRegistrarIngreso trabajadorSinCarnet(String tipoDocumento,
                                                              String numeroDocumento,
                                                              long empresaId,
                                                              long anfitrionId) {
        return new ComandoRegistrarIngreso(tipoDocumento, numeroDocumento,
                TipoVisita.OLVIDO_CARNET, empresaId, anfitrionId,
                "Ingreso sin carnet, requiere autorizacion del anfitrion", null);
    }
}
