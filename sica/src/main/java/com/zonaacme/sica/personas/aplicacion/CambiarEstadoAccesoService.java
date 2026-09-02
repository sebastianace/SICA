package com.zonaacme.sica.personas.aplicacion;

import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;

/**
 * Impone o levanta una prohibicion de ingreso.
 *
 * Es el caso de uso que conecta el modulo de incidentes con el de acceso: un
 * incidente grave desemboca aqui, y a partir de ese momento la porteria detiene
 * a esa persona en todas las garitas a la vez.
 */
public final class CambiarEstadoAccesoService
        implements CasoDeUso<ComandoCambiarEstadoAcceso, ResultadoPersona> {

    private final RepositorioPersonas repositorio;
    private final GestorTransacciones transacciones;

    public CambiarEstadoAccesoService(RepositorioPersonas repositorio,
                                      GestorTransacciones transacciones) {
        this.repositorio   = repositorio;
        this.transacciones = transacciones;
    }

    @Override
    public ResultadoPersona ejecutar(ComandoCambiarEstadoAcceso comando) {
        return transacciones.ejecutar(contexto -> {

            Persona persona = repositorio.buscarPorId(contexto, comando.personaId())
                    .orElseThrow(() -> new ExcepcionDominio("La persona ya no existe."));

            // Evita registrar en la bitacora un cambio que no cambia nada.
            if (persona.estaBloqueada() == comando.bloquear()) {
                throw new ExcepcionDominio(comando.bloquear()
                        ? "Esta persona ya tenia una restriccion activa."
                        : "Esta persona no tiene ninguna restriccion activa.");
            }

            repositorio.cambiarEstadoAcceso(contexto, comando);

            return new ResultadoPersona(persona.id(), persona.nombre(),
                    comando.bloquear()
                            ? "Restriccion impuesta. " + persona.nombre()
                            + " sera detenido en todas las porterias."
                            : "Restriccion levantada. " + persona.nombre()
                            + " puede ingresar nuevamente.");
        });
    }
}
