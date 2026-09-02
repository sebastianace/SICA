package com.zonaacme.sica.personas.aplicacion;

import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;

import java.util.List;
import java.util.Optional;

public interface RepositorioPersonas {

    long crear(ContextoTransaccion contexto, ComandoGuardarPersona comando);

    void actualizar(ContextoTransaccion contexto, ComandoGuardarPersona comando);

    void cambiarEstadoAcceso(ContextoTransaccion contexto, ComandoCambiarEstadoAcceso comando);

    Optional<Persona> buscarPorId(ContextoTransaccion contexto, long personaId);

    /** True si el documento ya pertenece a OTRA persona distinta de la indicada. */
    boolean documentoOcupado(ContextoTransaccion contexto, String documento, Long exceptoId);

    /** True si la persona tiene alguna visita registrada. */
    boolean tieneHistorial(ContextoTransaccion contexto, long personaId);

    void eliminar(ContextoTransaccion contexto, long personaId);

    List<Persona> listar(String filtro);
}
