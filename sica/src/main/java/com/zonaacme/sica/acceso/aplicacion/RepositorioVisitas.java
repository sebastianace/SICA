package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.Visita;
import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;

import java.util.List;
import java.util.Optional;

/** Puerto de salida de persistencia de visitas. */
public interface RepositorioVisitas {

    /**
     * Busca la visita abierta (estado DENTRO) de una persona BLOQUEANDO LA FILA.
     *
     * El bloqueo no es un detalle menor. MySQL no soporta indices unicos
     * parciales, asi que no se puede exigir a nivel de esquema que exista una
     * sola visita DENTRO por persona. Sin SELECT ... FOR UPDATE, dos guardas
     * registrando a la misma persona en el mismo instante crearian dos visitas
     * abiertas y se romperia la invariante del sistema.
     */
    Optional<Visita> buscarAbiertaBloqueando(ContextoTransaccion contexto, long personaId);

    /** Visita ya aprobada y todavia vigente para esa persona (flujo 1). */
    Optional<Visita> buscarAprobadaVigente(ContextoTransaccion contexto, long personaId);

    Optional<Visita> buscarPorId(ContextoTransaccion contexto, long visitaId);

    /** Inserta y devuelve la visita con su id asignado. */
    Visita crear(ContextoTransaccion contexto, Visita visita);

    /** Persiste el estado y las fechas tras una transicion del dominio. */
    void actualizar(ContextoTransaccion contexto, Visita visita);

    // ---- Consultas de solo lectura, fuera de transaccion ----

    /**
     * Devuelve el id y la hora de ingreso de la visita que la persona tiene
     * abierta, si la tiene. La porteria necesita ambos datos: el id para
     * poder cerrarla y la hora para mostrarle al guarda desde cuando figura
     * adentro esa persona.
     */
    Optional<VisitaAbiertaResumen> visitaAbiertaDe(long personaId);

    Optional<Long> idDeVisitaAprobadaVigente(long personaId);

    boolean tieneSolicitudEnEspera(long personaId);

    List<SolicitudPendiente> pendientesDeEmpresa(long empresaId);

    List<OcupanteActual> ocupacionActual();
}
