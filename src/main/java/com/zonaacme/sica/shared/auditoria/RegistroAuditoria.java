package com.zonaacme.sica.shared.auditoria;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Una linea de la bitacora de auditoria.
 * Es inmutable: una vez construida no se puede alterar antes de guardarse.
 */
public record RegistroAuditoria(
        Long          usuarioId,        // null en un login fallido: aun no hay sesion
        String        usuarioIntento,   // username tecleado cuando no hay sesion
        String        accion,
        String        entidadAfectada,
        Long          idEntidad,
        String        detalle,
        Resultado     resultado,
        String        terminal,
        LocalDateTime fechaHora
) {

    public enum Resultado { EXITO, FALLO }

    /**
     * La columna fecha_hora es DATETIME(3), o sea milisegundos. Se trunca aqui
     * para que el hash calculado en memoria coincida exactamente con el que se
     * recalcula al leer la fila desde MySQL. Sin esto, los nanosegundos de
     * LocalDateTime.now() harian que toda verificacion de integridad fallara.
     */
    private static LocalDateTime ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public static RegistroAuditoria exito(Long usuarioId, String accion, String entidad,
                                          Long idEntidad, String detalle, String terminal) {
        return new RegistroAuditoria(usuarioId, null, accion, entidad, idEntidad,
                detalle, Resultado.EXITO, terminal, ahora());
    }

    public static RegistroAuditoria fallo(Long usuarioId, String accion, String entidad,
                                          Long idEntidad, String detalle, String terminal) {
        return new RegistroAuditoria(usuarioId, null, accion, entidad, idEntidad,
                detalle, Resultado.FALLO, terminal, ahora());
    }

    /** Caso especial: intento de login. Todavia no existe un usuarioId valido. */
    public static RegistroAuditoria intentoLogin(String usernameIntentado, boolean exitoso,
                                                 Long usuarioId, String detalle, String terminal) {
        return new RegistroAuditoria(usuarioId, usernameIntentado, "LOGIN", "usuario",
                usuarioId, detalle,
                exitoso ? Resultado.EXITO : Resultado.FALLO,
                terminal, ahora());
    }
}
