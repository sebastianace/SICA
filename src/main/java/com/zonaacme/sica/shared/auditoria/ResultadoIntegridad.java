package com.zonaacme.sica.shared.auditoria;

/**
 * Veredicto de la verificacion de la cadena de auditoria.
 *
 * Si alguien edita una fila vieja directamente en MySQL, su hash deja de
 * coincidir con el recalculado y este objeto reporta exactamente en que
 * registro se rompio la cadena.
 */
public record ResultadoIntegridad(
        boolean intacta,
        int     registrosVerificados,
        Long    idPrimerRegistroAlterado,
        String  mensaje
) {

    public static ResultadoIntegridad intacta(int verificados) {
        return new ResultadoIntegridad(true, verificados, null,
                "Cadena integra. Se verificaron " + verificados + " registros sin alteraciones.");
    }

    public static ResultadoIntegridad rota(int verificados, long idAlterado) {
        return new ResultadoIntegridad(false, verificados, idAlterado,
                "Cadena de integridad ROTA en el registro #" + idAlterado
              + ". El contenido de ese registro no coincide con su hash almacenado.");
    }
}
