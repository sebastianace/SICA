package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.PersonaEnPorteria;
import com.zonaacme.sica.acceso.dominio.SolicitudDeIngresoCreada;
import com.zonaacme.sica.acceso.dominio.TipoVisita;
import com.zonaacme.sica.acceso.dominio.Visita;
import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;
import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;
import com.zonaacme.sica.shared.eventos.PublicadorEventos;
import com.zonaacme.sica.shared.seguridad.SesionActual;

import java.util.Optional;

/**
 * Caso de uso central del sistema. Aqui conviven los cuatro flujos del enunciado.
 *
 * INVARIANTE QUE PROTEGE ESTE SERVICIO:
 *   una persona no puede tener mas de una visita en estado DENTRO al mismo tiempo.
 *
 * El flujo 4 del enunciado (la salida olvidada) existe justamente para sostener
 * esa invariante: si alguien vuelve a aparecer en la porteria y todavia figura
 * adentro, el sistema cierra la visita vieja marcandola como inconsistencia y
 * abre una nueva. Nunca se le niega el ingreso, pero nunca se pierde el rastro.
 *
 * Las dos escrituras van dentro de la misma transaccion. Si se cayera entre una
 * y otra, la persona quedaria con dos visitas abiertas y la invariante se
 * rompe. Por eso el cierre y la creacion viven o mueren juntos.
 *
 * Nota sobre lo que NO esta aqui: no hay verificacion de permisos ni escritura
 * en la bitacora. Ambas cosas las aportan los decoradores al ensamblar la
 * aplicacion. Esta clase solo sabe de reglas de acceso.
 */
public final class RegistrarIngresoService implements CasoDeUso<ComandoRegistrarIngreso, ResultadoIngreso> {

    private final RepositorioVisitas repositorio;
    private final ConsultaPorteria consulta;
    private final GestorTransacciones transacciones;
    private final PublicadorEventos publicador;
    private final SesionActual sesion;

    public RegistrarIngresoService(RepositorioVisitas repositorio,
                                   ConsultaPorteria consulta,
                                   GestorTransacciones transacciones,
                                   PublicadorEventos publicador,
                                   SesionActual sesion) {
        this.repositorio = repositorio;
        this.consulta = consulta;
        this.transacciones = transacciones;
        this.publicador = publicador;
        this.sesion = sesion;
    }

    @Override
    public ResultadoIngreso ejecutar(ComandoRegistrarIngreso comando) {

        long guardaId = sesion.obligatorio().id();

        ResultadoIngreso resultado = transacciones.ejecutar(contexto ->
                registrarDentroDeTransaccion(contexto, comando, guardaId));

        // La notificacion sale DESPUES del commit. Si se publicara dentro de la
        // transaccion y luego hubiera rollback, se habria avisado al funcionario
        // de una solicitud que no existe.
        if (resultado.esperaAprobacion()) {
            publicador.publicar(new SolicitudDeIngresoCreada(
                    resultado.visitaId(),
                    resultado.persona().id(),
                    resultado.persona().nombreCompleto(),
                    resultado.persona().documentoFormateado(),
                    resultado.persona().fotoUrl(),
                    comando.empresaDestinoId() == null ? 0L : comando.empresaDestinoId(),
                    comando.anfitrionUsuarioId(),
                    resultado.tipo(),
                    comando.motivo(),
                    java.time.LocalDateTime.now()));
        }

        return resultado;
    }

    private ResultadoIngreso registrarDentroDeTransaccion(ContextoTransaccion contexto,
                                                          ComandoRegistrarIngreso comando,
                                                          long guardaId) {

        // 1. Identificar a la persona.
        PersonaEnPorteria persona = consulta
                .buscarPorDocumento(contexto, comando.tipoDocumento(), comando.numeroDocumento())
                .orElseThrow(() -> new ExcepcionDominio(
                        "No existe ninguna persona con documento "
                      + comando.tipoDocumento() + " " + comando.numeroDocumento() + "."));

        // 2. La restriccion de acceso corta el flujo sin excepcion.
        if (persona.bloqueada()) {
            throw new ExcepcionDominio("Ingreso denegado. "
                    + persona.nombreCompleto() + " tiene una restriccion de acceso activa: "
                    + persona.motivoBloqueo());
        }

        // 3. FLUJO 4. Se bloquea la fila para que dos porterias simultaneas no
        //    puedan crear dos visitas abiertas para la misma persona.
        Optional<Visita> visitaOlvidada = repositorio.buscarAbiertaBloqueando(contexto, persona.id());
        Long idVisitaCerrada = null;

        if (visitaOlvidada.isPresent()) {
            Visita anterior = visitaOlvidada.get();
            anterior.cerrarPorSalidaOlvidada();
            repositorio.actualizar(contexto, anterior);
            idVisitaCerrada = anterior.id();
        }

        // 4. Crear o retomar la visita segun el flujo.
        Visita visita = (comando.visitaPreAprobadaId() != null)
                ? hacerCheckInDeVisitaAprobada(contexto, comando, persona)
                : crearVisitaNueva(contexto, comando, persona, guardaId);

        return new ResultadoIngreso(
                visita.id(),
                visita.estado(),
                visita.tipo(),
                persona,
                idVisitaCerrada != null,
                idVisitaCerrada,
                construirMensaje(visita, persona, idVisitaCerrada));
    }

    /** FLUJO 1. La visita ya existia aprobada: solo se marca la entrada. */
    private Visita hacerCheckInDeVisitaAprobada(ContextoTransaccion contexto,
                                                ComandoRegistrarIngreso comando,
                                                PersonaEnPorteria persona) {

        Visita visita = repositorio.buscarPorId(contexto, comando.visitaPreAprobadaId())
                .orElseThrow(() -> new ExcepcionDominio("La visita aprobada ya no existe."));

        if (visita.personaId() != persona.id()) {
            throw new ExcepcionDominio(
                    "La visita aprobada pertenece a otra persona. Verifica el documento.");
        }

        // La transicion APROBADA -> DENTRO la valida la maquina de estados.
        visita.registrarIngreso();
        repositorio.actualizar(contexto, visita);
        return visita;
    }

    /** FLUJOS 2, 3 y rutina de trabajador. */
    private Visita crearVisitaNueva(ContextoTransaccion contexto,
                                    ComandoRegistrarIngreso comando,
                                    PersonaEnPorteria persona,
                                    long guardaId) {

        if (comando.empresaDestinoId() == null) {
            throw new ExcepcionDominio("Falta indicar a que empresa se dirige la persona.");
        }
        long empresaId = comando.empresaDestinoId();

        Visita nueva = switch (comando.tipo()) {

            case RUTINA_TRABAJADOR -> Visita.rutinaDeTrabajador(persona.id(), empresaId, guardaId);

            case NO_ANUNCIADA -> Visita.noAnunciada(persona.id(), empresaId,
                    exigirAnfitrion(comando), comando.motivo(), guardaId);

            case OLVIDO_CARNET -> Visita.porOlvidoDeCarnet(persona.id(), empresaId,
                    exigirAnfitrion(comando), guardaId);

            case PRE_REGISTRADA -> throw new ExcepcionDominio(
                    "Una visita pre-registrada no se crea desde la porteria. "
                  + "La registra el funcionario de la empresa con anticipacion.");
        };

        return repositorio.crear(contexto, nueva);
    }

    private long exigirAnfitrion(ComandoRegistrarIngreso comando) {
        if (comando.anfitrionUsuarioId() == null) {
            throw new ExcepcionDominio(
                    "Este ingreso necesita un anfitrion que lo autorice. Selecciona a quien visita.");
        }
        return comando.anfitrionUsuarioId();
    }

    private String construirMensaje(Visita visita, PersonaEnPorteria persona, Long idVisitaCerrada) {
        StringBuilder mensaje = new StringBuilder();

        switch (visita.estado()) {
            case DENTRO -> mensaje.append("Ingreso registrado. ")
                                  .append(persona.nombreCompleto())
                                  .append(" esta dentro del complejo.");
            case PENDIENTE_APROBACION -> mensaje.append(
                    "Solicitud enviada al anfitrion. Espera su respuesta en pantalla.");
            case PENDIENTE_APROBACION_OLVIDO -> mensaje.append(
                    "Solicitud por olvido de carnet enviada al anfitrion.");
            default -> mensaje.append("Visita registrada en estado ")
                              .append(visita.estado().etiqueta())
                              .append(".");
        }

        if (idVisitaCerrada != null) {
            mensaje.append(" Atencion: la visita #").append(idVisitaCerrada)
                   .append(" habia quedado abierta sin registro de salida y el sistema ")
                   .append("la cerro automaticamente para auditoria.");
        }
        return mensaje.toString();
    }
}
