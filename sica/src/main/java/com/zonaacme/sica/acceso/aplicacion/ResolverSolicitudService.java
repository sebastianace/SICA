package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.SolicitudDeIngresoResuelta;
import com.zonaacme.sica.acceso.dominio.Visita;
import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;
import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;
import com.zonaacme.sica.shared.eventos.PublicadorEventos;
import com.zonaacme.sica.shared.seguridad.SesionActual;

import java.time.LocalDateTime;

/**
 * FLUJOS 2 y 3: el funcionario responde a una solicitud de ingreso.
 *
 * Es la contraparte de RegistrarIngresoService. Uno crea la solicitud desde la
 * porteria y publica el evento; este la resuelve y publica el evento de vuelta,
 * que es lo que hace que la pantalla del guarda se actualice sola.
 *
 * El enunciado trata el invitado no anunciado y el trabajador sin carnet como
 * dos flujos, pero desde aqui son el mismo: cambia el TipoVisita, no la
 * decision. Por eso hay un solo caso de uso y no dos casi identicos.
 *
 * Igual que en el ingreso, el evento se publica DESPUES del commit.
 */
public final class ResolverSolicitudService
        implements CasoDeUso<ComandoResolverSolicitud, ResultadoResolucion> {

    private final RepositorioVisitas   repositorio;
    private final ConsultaPorteria     consulta;
    private final GestorTransacciones  transacciones;
    private final PublicadorEventos    publicador;
    private final SesionActual         sesion;

    public ResolverSolicitudService(RepositorioVisitas repositorio,
                                    ConsultaPorteria consulta,
                                    GestorTransacciones transacciones,
                                    PublicadorEventos publicador,
                                    SesionActual sesion) {
        this.repositorio   = repositorio;
        this.consulta      = consulta;
        this.transacciones = transacciones;
        this.publicador    = publicador;
        this.sesion        = sesion;
    }

    @Override
    public ResultadoResolucion ejecutar(ComandoResolverSolicitud comando) {

        var funcionario = sesion.obligatorio();

        ResultadoResolucion resultado = transacciones.ejecutar(contexto ->
                resolverDentroDeTransaccion(contexto, comando, funcionario.id()));

        // El personaId viaja en el evento para que la pantalla del guarda pueda
        // decidir si la respuesta es sobre la persona que tiene enfrente.
        publicador.publicar(new SolicitudDeIngresoResuelta(
                resultado.visitaId(),
                resultado.personaId(),
                resultado.nombrePersona(),
                resultado.estado(),
                funcionario.nombreCompleto(),
                comando.observaciones(),
                LocalDateTime.now()));

        return resultado;
    }

    private ResultadoResolucion resolverDentroDeTransaccion(ContextoTransaccion contexto,
                                                            ComandoResolverSolicitud comando,
                                                            long funcionarioId) {

        Visita visita = repositorio.buscarPorId(contexto, comando.visitaId())
                .orElseThrow(() -> new ExcepcionDominio(
                        "La solicitud #" + comando.visitaId() + " ya no existe."));

        // Si otro funcionario de la misma empresa alcanzo a responder primero,
        // el dominio lo detecta: el estado ya no espera aprobacion. Es la
        // condicion de carrera real de este flujo, no una hipotesis.
        if (!visita.estado().esperaAprobacion()) {
            throw new ExcepcionDominio(
                    "Esta solicitud ya fue resuelta. Estado actual: " + visita.estado().name() + ".");
        }

        String nombre = consulta.nombreDePersona(contexto, visita.personaId());

        if (comando.aprobar()) {
            visita.aprobar(funcionarioId);
        } else {
            visita.rechazar(funcionarioId,
                    comando.observaciones() == null || comando.observaciones().isBlank()
                            ? "Rechazada por el anfitrion sin observaciones."
                            : comando.observaciones());
        }

        repositorio.actualizar(contexto, visita);

        return new ResultadoResolucion(
                visita.id(),
                visita.personaId(),
                visita.estado(),
                nombre,
                comando.aprobar(),
                comando.aprobar()
                        ? "Ingreso autorizado. El guarda ya puede dejar pasar a " + nombre + "."
                        : "Ingreso rechazado. Se notifico a la porteria.");
    }
}
