package com.zonaacme.sica.incidentes.aplicacion;

import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.seguridad.SesionActual;

/**
 * Registra un incidente de seguridad.
 *
 * Quien lo reporta sale de la sesion, no del comando. Si el usuario pudiera
 * indicar a nombre de quien reporta, el dato dejaria de servir como evidencia.
 */
public final class RegistrarIncidenteService
        implements CasoDeUso<ComandoRegistrarIncidente, ResultadoIncidente> {

    private final RepositorioIncidentes repositorio;
    private final GestorTransacciones   transacciones;
    private final SesionActual          sesion;

    public RegistrarIncidenteService(RepositorioIncidentes repositorio,
                                     GestorTransacciones transacciones,
                                     SesionActual sesion) {
        this.repositorio   = repositorio;
        this.transacciones = transacciones;
        this.sesion        = sesion;
    }

    @Override
    public ResultadoIncidente ejecutar(ComandoRegistrarIncidente comando) {
        long autorId = sesion.obligatorio().id();

        long id = transacciones.ejecutar(contexto ->
                repositorio.crear(contexto, comando, autorId));

        // La regla vive en el enum del dominio, no aqui ni en la pantalla.
        boolean sugiereBloqueo = comando.gravedad().requiereBloqueoInmediato()
                              && comando.personaId() != null;

        return new ResultadoIncidente(
                id,
                comando.gravedad(),
                sugiereBloqueo,
                sugiereBloqueo
                        ? "Incidente registrado. Por su gravedad, considera imponer "
                        + "prohibicion de ingreso a esta persona."
                        : "Incidente registrado con exito.");
    }
}
