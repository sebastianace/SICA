package com.zonaacme.sica.incidentes.aplicacion;

import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;

/**
 * Cierra un incidente. Exige una conclusion escrita: un incidente cerrado sin
 * explicacion no sirve para nada cuando alguien lo revise meses despues.
 */
public final class CerrarIncidenteService
        implements CasoDeUso<ComandoCerrarIncidente, ResultadoIncidente> {

    private final RepositorioIncidentes repositorio;
    private final GestorTransacciones   transacciones;

    public CerrarIncidenteService(RepositorioIncidentes repositorio,
                                  GestorTransacciones transacciones) {
        this.repositorio   = repositorio;
        this.transacciones = transacciones;
    }

    @Override
    public ResultadoIncidente ejecutar(ComandoCerrarIncidente comando) {
        return transacciones.ejecutar(contexto -> {

            Incidente incidente = repositorio.buscarPorId(contexto, comando.incidenteId())
                    .orElseThrow(() -> new ExcepcionDominio(
                            "El incidente #" + comando.incidenteId() + " no existe."));

            // La regla la responde el enum del dominio, no un if suelto aqui.
            if (!incidente.estado().permiteCierre()) {
                throw new ExcepcionDominio("Este incidente ya estaba cerrado.");
            }

            repositorio.cerrar(contexto, comando.incidenteId(), comando.conclusion());

            return new ResultadoIncidente(
                    incidente.id(), incidente.gravedad(), false,
                    "Incidente cerrado.");
        });
    }
}
