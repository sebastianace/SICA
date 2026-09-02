package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.PersonaEnPorteria;
import com.zonaacme.sica.acceso.dominio.Visita;
import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;

/** Check-out. DENTRO -> FINALIZADA. */
public final class RegistrarSalidaService implements CasoDeUso<ComandoRegistrarSalida, ResultadoSalida> {

    private final RepositorioVisitas repositorio;
    private final ConsultaPorteria consulta;
    private final GestorTransacciones transacciones;

    public RegistrarSalidaService(RepositorioVisitas repositorio,
                                  ConsultaPorteria consulta,
                                  GestorTransacciones transacciones) {
        this.repositorio = repositorio;
        this.consulta = consulta;
        this.transacciones = transacciones;
    }

    @Override
    public ResultadoSalida ejecutar(ComandoRegistrarSalida comando) {
        return transacciones.ejecutar(contexto -> {

            PersonaEnPorteria persona = consulta
                    .buscarPorDocumento(contexto, comando.tipoDocumento(), comando.numeroDocumento())
                    .orElseThrow(() -> new ExcepcionDominio("No existe ninguna persona con ese documento."));

            Visita visita = repositorio.buscarAbiertaBloqueando(contexto, persona.id())
                    .orElseThrow(() -> new ExcepcionDominio(
                            persona.nombreCompleto() + " no figura dentro del complejo. "
                          + "No hay ninguna visita abierta que cerrar."));

            visita.registrarSalida();
            repositorio.actualizar(contexto, visita);

            return new ResultadoSalida(visita.id(), persona.nombreCompleto(),
                    "Salida registrada. " + persona.nombreCompleto() + " ya no figura en el complejo.");
        });
    }
}
