package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.FichaPorteria;
import com.zonaacme.sica.acceso.dominio.PersonaEnPorteria;
import com.zonaacme.sica.acceso.dominio.Veredicto;
import com.zonaacme.sica.shared.aplicacion.CasoDeUso;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Resuelve el veredicto de la porteria.
 *
 * Esta clase concentra la pregunta "puede entrar o no" para que la pantalla no
 * tenga que decidir nada. La interfaz solo pinta el color y la palabra que este
 * servicio ya escogio. Si manana hay que agregar una regla nueva (por ejemplo,
 * negar el ingreso fuera del horario del complejo), se agrega aqui y las tres
 * pantallas que consultan porteria quedan consistentes solas.
 *
 * Fijate en el orden de las reglas: el bloqueo se evalua de primero y corta
 * todo. Ninguna visita aprobada le gana a una restriccion de acceso.
 */
public final class ConsultarPorteriaService implements CasoDeUso<ComandoConsultarPorteria, FichaPorteria> {

    private final ConsultaPorteria consulta;
    private final RepositorioVisitas repositorio;

    public ConsultarPorteriaService(ConsultaPorteria consulta, RepositorioVisitas repositorio) {
        this.consulta = consulta;
        this.repositorio = repositorio;
    }

    @Override
    public FichaPorteria ejecutar(ComandoConsultarPorteria comando) {

        Optional<PersonaEnPorteria> encontrada =
                consulta.buscarPorDocumento(comando.tipoDocumento(), comando.numeroDocumento());

        if (encontrada.isEmpty()) {
            return FichaPorteria.noRegistrado(comando.tipoDocumento(), comando.numeroDocumento());
        }

        PersonaEnPorteria persona = encontrada.get();

        // Regla 1. La restriccion de acceso gana sobre cualquier otra cosa.
        if (persona.bloqueada()) {
            return new FichaPorteria(persona, Veredicto.BLOQUEADO,
                    persona.motivoBloqueo() == null
                            ? "Esta persona tiene una restriccion de acceso activa."
                            : persona.motivoBloqueo(),
                    null, null, null, null, null, null);
        }

        // Dato informativo, no bloqueante: quedo una visita sin cerrar.
        // Se lleva tambien la hora de ingreso para que el guarda vea desde
        // cuando figura adentro y entienda por que se le ofrece regularizar.
        VisitaAbiertaResumen abierta = repositorio.visitaAbiertaDe(persona.id()).orElse(null);
        Long          visitaAbiertaId = abierta == null ? null : abierta.visitaId();
        LocalDateTime ingresoAbierta  = abierta == null ? null : abierta.fechaIngreso();

        // Regla 2. Ya hay una solicitud esperando respuesta del anfitrion.
        if (repositorio.tieneSolicitudEnEspera(persona.id())) {
            return new FichaPorteria(persona, Veredicto.PENDIENTE,
                    "Ya hay una solicitud enviada al anfitrion. Espera la respuesta en pantalla.",
                    null, visitaAbiertaId, ingresoAbierta,
                    persona.empresaId(), persona.empresaNombre(), null);
        }

        // Regla 3. Flujo 1: hay una visita aprobada esperandolo.
        Optional<Long> visitaAprobada = repositorio.idDeVisitaAprobadaVigente(persona.id());
        if (visitaAprobada.isPresent()) {
            return new FichaPorteria(persona, Veredicto.AUTORIZADO,
                    "Visita aprobada y vigente. Puedes registrar el ingreso.",
                    visitaAprobada.get(), visitaAbiertaId, ingresoAbierta,
                    persona.empresaId(), persona.empresaNombre(), null);
        }

        // Regla 4. Trabajador identificado: entra por rutina, sin aprobacion.
        if (persona.esTrabajador() && persona.empresaId() != null) {
            return new FichaPorteria(persona, Veredicto.AUTORIZADO,
                    "Trabajador de " + persona.empresaNombre() + ". Ingreso de rutina.",
                    null, visitaAbiertaId, ingresoAbierta,
                    persona.empresaId(), persona.empresaNombre(), null);
        }

        // Regla 5. Invitado sin cita: flujo 2, hay que pedirle permiso al anfitrion.
        return new FichaPorteria(persona, Veredicto.REQUIERE_AUTORIZACION,
                "No tiene visita aprobada. Selecciona la empresa y envia la solicitud al anfitrion.",
                null, visitaAbiertaId, ingresoAbierta,
                persona.empresaId(), persona.empresaNombre(), null);
    }
}
