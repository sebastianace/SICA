package com.zonaacme.sica.shared.auditoria;

import java.util.List;

/**
 * Puerto de salida de la auditoria.
 *
 * El dominio y la aplicacion solo conocen esta interfaz. El adaptador que
 * escribe en MySQL vive en shared/infraestructura y podria reemplazarse por uno
 * que escriba en archivo o en un servicio externo sin tocar el nucleo.
 */
public interface Bitacora {

    /** Persiste un registro. Nunca debe hacer fallar la operacion de negocio. */
    void registrar(RegistroAuditoria registro);

    /** Consulta paginada, del mas reciente al mas antiguo. */
    List<LineaBitacora> consultarUltimos(int limite);

    /**
     * Recorre la cadena de hashes y devuelve el resultado de la verificacion.
     * Convierte la palabra "inmutable" del enunciado en algo demostrable.
     */
    ResultadoIntegridad verificarIntegridad();
}
