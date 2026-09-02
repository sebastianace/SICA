package com.zonaacme.sica.incidentes.aplicacion;

import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;
import java.util.List;

public interface RepositorioIncidentes {

    long crear(ContextoTransaccion contexto, ComandoRegistrarIncidente comando, long reportadoPorId);

    void cerrar(ContextoTransaccion contexto, long incidenteId, String conclusion);

    /** Estado actual, para que el caso de uso valide antes de cerrar. */
    java.util.Optional<Incidente> buscarPorId(ContextoTransaccion contexto, long incidenteId);

    List<Incidente> listarTodos();
}
