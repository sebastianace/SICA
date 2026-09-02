package com.zonaacme.sica.personas.aplicacion;

import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;

/**
 * Elimina una persona, solo si nunca cruzo la porteria.
 *
 * Con historial de visitas no se borra, y no es una limitacion tecnica: borrar
 * a alguien que estuvo dentro del complejo destruiria el registro de quien
 * entro y cuando. Para esos casos existe el bloqueo, que impide el ingreso sin
 * borrar la historia.
 */
public final class EliminarPersonaService
        implements CasoDeUso<ComandoEliminarPersona, ResultadoPersona> {

    private final RepositorioPersonas repositorio;
    private final GestorTransacciones transacciones;

    public EliminarPersonaService(RepositorioPersonas repositorio,
                                  GestorTransacciones transacciones) {
        this.repositorio   = repositorio;
        this.transacciones = transacciones;
    }

    @Override
    public ResultadoPersona ejecutar(ComandoEliminarPersona comando) {
        return transacciones.ejecutar(contexto -> {

            Persona persona = repositorio.buscarPorId(contexto, comando.personaId())
                    .orElseThrow(() -> new ExcepcionDominio("La persona ya no existe."));

            if (repositorio.tieneHistorial(contexto, comando.personaId())) {
                throw new ExcepcionDominio(
                        persona.nombre() + " tiene visitas registradas y no se puede eliminar "
                      + "sin destruir el historial de acceso. Si el objetivo es impedirle la "
                      + "entrada, impon una restriccion de ingreso.");
            }

            repositorio.eliminar(contexto, comando.personaId());
            return new ResultadoPersona(persona.id(), persona.nombre(),
                    persona.nombre() + " fue eliminado del sistema.");
        });
    }
}
