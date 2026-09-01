package com.zonaacme.sica.shared.infraestructura;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Calcula el eslabon de la cadena de integridad de la bitacora.
 *
 * Cada registro incluye en su propio hash el hash del registro anterior. Eso
 * convierte la tabla en una cadena: si alguien edita una fila vieja con un
 * cliente SQL, su hash deja de coincidir con el recalculado y todos los
 * registros posteriores quedan huerfanos. La alteracion es detectable y
 * ademas se puede senalar exactamente donde ocurrio.
 *
 * Es la diferencia entre decir que la bitacora es inmutable y poder demostrarlo.
 */
public final class CalculadorHashBitacora {

    /** Eslabon cero de la cadena. El primer registro del sistema apunta aqui. */
    public static final String GENESIS = "0".repeat(64);

    private static final String NULO = "~";
    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private CalculadorHashBitacora() { }

    public static String calcular(long id, Long usuarioId, String usuarioIntento,
                                  String accion, String entidad, Long idEntidad,
                                  String detalle, String resultado, String terminal,
                                  LocalDateTime fechaHora, String hashAnterior) {

        String cadena = String.join("|",
                String.valueOf(id),
                texto(usuarioId),
                texto(usuarioIntento),
                texto(accion),
                texto(entidad),
                texto(idEntidad),
                texto(detalle),
                texto(resultado),
                texto(terminal),
                fechaHora == null ? NULO : FORMATO.format(fechaHora),
                hashAnterior == null ? GENESIS : hashAnterior);

        return sha256Hex(cadena);
    }

    private static String texto(Object valor) {
        return valor == null ? NULO : valor.toString();
    }

    private static String sha256Hex(String entrada) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(entrada.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
