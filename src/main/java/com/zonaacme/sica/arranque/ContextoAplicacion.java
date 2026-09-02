package com.zonaacme.sica.arranque;

import com.zonaacme.sica.acceso.aplicacion.*;
import com.zonaacme.sica.acceso.dominio.FichaPorteria;
import com.zonaacme.sica.acceso.infraestructura.JdbcConsultaPorteria;
import com.zonaacme.sica.acceso.infraestructura.JdbcRepositorioVisitas;
import com.zonaacme.sica.directorio.aplicacion.Directorio;
import com.zonaacme.sica.directorio.infraestructura.JdbcDirectorio;
import com.zonaacme.sica.incidentes.aplicacion.*;
import com.zonaacme.sica.personas.aplicacion.*;
import com.zonaacme.sica.personas.infraestructura.JdbcRepositorioPersonas;
import com.zonaacme.sica.incidentes.infraestructura.JdbcRepositorioIncidentes;
import com.zonaacme.sica.reportes.aplicacion.*;
import com.zonaacme.sica.reportes.infraestructura.JdbcConsultaReportes;
import com.zonaacme.sica.shared.aplicacion.*;
import com.zonaacme.sica.shared.auditoria.Bitacora;
import com.zonaacme.sica.shared.eventos.BusEventosEnMemoria;
import com.zonaacme.sica.shared.eventos.PublicadorEventos;
import com.zonaacme.sica.shared.infraestructura.ConfiguracionApp;
import com.zonaacme.sica.shared.infraestructura.GestorTransaccionesJdbc;
import com.zonaacme.sica.shared.infraestructura.JdbcBitacora;
import com.zonaacme.sica.shared.seguridad.SesionActual;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;
import com.zonaacme.sica.usuarios.aplicacion.ComandoLogin;
import com.zonaacme.sica.usuarios.aplicacion.IniciarSesionService;
import com.zonaacme.sica.usuarios.aplicacion.RepositorioUsuarios;
import com.zonaacme.sica.usuarios.infraestructura.JdbcRepositorioUsuarios;

/**
 * RAIZ DE COMPOSICION. Es el unico lugar de todo el sistema donde se decide
 * que implementacion concreta recibe cada puerto.
 *
 * Aqui esta la inversion de dependencias en su forma mas visible: los servicios
 * de negocio piden interfaces por constructor y esta clase les entrega los
 * adaptadores de MySQL. Cambiar de motor de base de datos, o poner
 * implementaciones en memoria para probar, se hace en este archivo y en ningun
 * otro.
 *
 * Tambien es donde se aplican los decoradores. Fijate en que cada caso de uso
 * declara aqui su permiso y su accion de bitacora: son configuracion del
 * ensamblado, no logica de negocio, y por eso viven fuera de los servicios.
 *
 * COMPARTIDO vs POR SESION:
 *   Los adaptadores y el bus de eventos son estaticos: hay uno por aplicacion.
 *   El bus DEBE ser compartido, porque es el canal por el que la ventana del
 *   guarda y la del funcionario se hablan.
 *   La sesion es de instancia: en la demostracion se abren dos ventanas con dos
 *   usuarios distintos en la misma JVM, y cada una necesita la suya.
 */
public final class ContextoAplicacion {

    // ---- Compartidos por toda la aplicacion ----
    private static final PublicadorEventos    BUS           = new BusEventosEnMemoria();
    private static final Bitacora             BITACORA      = new JdbcBitacora();
    private static final GestorTransacciones  TRANSACCIONES = new GestorTransaccionesJdbc();
    private static final RepositorioVisitas   VISITAS       = new JdbcRepositorioVisitas();
    private static final ConsultaPorteria     PORTERIA      = new JdbcConsultaPorteria();
    private static final RepositorioUsuarios  USUARIOS      = new JdbcRepositorioUsuarios();
    private static final RepositorioIncidentes INCIDENTES   = new JdbcRepositorioIncidentes();
    private static final ConsultaReportes     REPORTES      = new JdbcConsultaReportes();
    private static final Directorio           DIRECTORIO    = new JdbcDirectorio();
    private static final RepositorioPersonas  PERSONAS      = new JdbcRepositorioPersonas();

    // ---- Propios de esta sesion ----
    private final SesionActual sesion;
    private final FabricaDecoradores decoradores;

    public ContextoAplicacion() {
        this.sesion = new SesionActual(ConfiguracionApp.terminal());
        this.decoradores = new FabricaDecoradores(sesion, BITACORA);
    }

    public SesionActual sesion()        { return sesion; }
    public PublicadorEventos bus()      { return BUS; }
    public Bitacora bitacora()          { return BITACORA; }
    public RepositorioVisitas visitas() { return VISITAS; }
    public RepositorioIncidentes incidentes() { return INCIDENTES; }
    public Directorio directorio() { return DIRECTORIO; }
    public RepositorioPersonas personas() { return PERSONAS; }

    /**
     * El login va SIN decoradores. No puede exigir un permiso porque todavia no
     * hay sesion, y su auditoria necesita registrar el username tecleado en un
     * intento fallido, algo que el decorador generico no puede saber.
     * Es la unica excepcion del sistema y esta documentada en el servicio.
     */
    public CasoDeUso<ComandoLogin, UsuarioAutenticado> iniciarSesion() {
        return new IniciarSesionService(USUARIOS, BITACORA, sesion);
    }

    public CasoDeUso<ComandoConsultarPorteria, FichaPorteria> consultarPorteria() {
        return decoradores
                .envolver(new ConsultarPorteriaService(PORTERIA, VISITAS))
                .conPermiso("consultar_porteria")
                .auditando("CONSULTA_PORTERIA", "persona",
                        (comando, ficha) -> DescripcionAuditoria.de(
                                ficha.hayPersona() ? ficha.persona().id() : null,
                                "Consulta de " + comando.tipoDocumento() + " " + comando.numeroDocumento()
                              + " -> veredicto " + ficha.veredicto().name()))
                .describiendoFallo(comando ->
                        "Consulta fallida de " + comando.tipoDocumento() + " " + comando.numeroDocumento())
                .construir();
    }

    public CasoDeUso<ComandoRegistrarIngreso, ResultadoIngreso> registrarIngreso() {
        return decoradores
                .envolver(new RegistrarIngresoService(VISITAS, PORTERIA, TRANSACCIONES, BUS, sesion))
                .conPermiso("registrar_visita")
                .auditando("REGISTRAR_INGRESO", "visita",
                        (comando, resultado) -> DescripcionAuditoria.de(
                                resultado.visitaId(),
                                "Ingreso de " + resultado.persona().nombreCompleto()
                              + " (" + resultado.persona().documentoFormateado() + ")"
                              + " tipo " + resultado.tipo().name()
                              + " estado " + resultado.estado().name()
                              + (resultado.cerroVisitaOlvidada()
                                    ? " | SALIDA OLVIDADA: se cerro la visita #"
                                      + resultado.visitaCerradaId()
                                    : "")))
                .describiendoFallo(comando ->
                        "Intento de ingreso de " + comando.tipoDocumento() + " " + comando.numeroDocumento())
                .construir();
    }

    public CasoDeUso<ComandoRegistrarSalida, ResultadoSalida> registrarSalida() {
        return decoradores
                .envolver(new RegistrarSalidaService(VISITAS, PORTERIA, TRANSACCIONES))
                .conPermiso("checkout_visita")
                .auditando("REGISTRAR_SALIDA", "visita",
                        (comando, resultado) -> DescripcionAuditoria.de(
                                resultado.visitaId(),
                                "Salida de " + resultado.nombrePersona()))
                .describiendoFallo(comando ->
                        "Intento de salida de " + comando.tipoDocumento() + " " + comando.numeroDocumento())
                .construir();
    }

    /**
     * FLUJOS 2 y 3. El permiso es 'aprobar_visita': un guarda no puede
     * autorizar su propia solicitud, solo el funcionario anfitrion.
     */
    public CasoDeUso<ComandoResolverSolicitud, ResultadoResolucion> resolverSolicitud() {
        return decoradores
                .envolver(new ResolverSolicitudService(VISITAS, PORTERIA, TRANSACCIONES, BUS, sesion))
                .conPermiso("aprobar_visita")
                .auditando("RESOLVER_SOLICITUD", "visita",
                        (comando, resultado) -> DescripcionAuditoria.de(
                                resultado.visitaId(),
                                (resultado.aprobada() ? "Aprobada" : "Rechazada")
                              + " la solicitud de ingreso de " + resultado.nombrePersona()
                              + (comando.observaciones() == null ? ""
                                    : " | Observaciones: " + comando.observaciones())))
                .describiendoFallo(comando ->
                        "Intento de resolver la solicitud #" + comando.visitaId())
                .construir();
    }

    // ==================================================================
    //  INCIDENTES
    // ==================================================================

    public CasoDeUso<ComandoRegistrarIncidente, ResultadoIncidente> registrarIncidente() {
        return decoradores
                .envolver(new RegistrarIncidenteService(INCIDENTES, TRANSACCIONES, sesion))
                .conPermiso("registrar_incidente")
                .auditando("REGISTRAR_INCIDENTE", "incidentes",
                        (comando, resultado) -> DescripcionAuditoria.de(
                                resultado.incidenteId(),
                                "Incidente " + comando.tipo().name()
                              + " de gravedad " + comando.gravedad().name()
                              + (comando.personaId() == null ? ""
                                    : " | Persona #" + comando.personaId())))
                .describiendoFallo(comando ->
                        "Intento de registrar un incidente de tipo " + comando.tipo().name())
                .construir();
    }

    public CasoDeUso<ComandoCerrarIncidente, ResultadoIncidente> cerrarIncidente() {
        return decoradores
                .envolver(new CerrarIncidenteService(INCIDENTES, TRANSACCIONES))
                .conPermiso("cerrar_incidente")
                .auditando("CERRAR_INCIDENTE", "incidentes",
                        (comando, resultado) -> DescripcionAuditoria.de(
                                comando.incidenteId(),
                                "Incidente cerrado | Conclusion: " + comando.conclusion()))
                .describiendoFallo(comando ->
                        "Intento de cerrar el incidente #" + comando.incidenteId())
                .construir();
    }

    // ==================================================================
    //  REPORTES
    // ==================================================================

    /**
     * El reporte se audita igual que cualquier otra operacion. Saber quien
     * consulto que datos y cuando es parte de la trazabilidad: un reporte de
     * visitas contiene informacion personal de todo el que entro al complejo.
     */
    public CasoDeUso<ComandoReporteVisitas, ReporteVisitas> generarReporteVisitas() {
        return decoradores
                .envolver(new GenerarReporteVisitasService(REPORTES))
                .conPermiso("generar_reporte")
                .auditando("GENERAR_REPORTE_VISITAS", "visitas",
                        (comando, resultado) -> DescripcionAuditoria.soloDetalle(
                                "Reporte de visitas del " + comando.desde()
                              + " al " + comando.hasta()
                              + (comando.empresaId() == null ? " (todo el complejo)"
                                    : " | Empresa #" + comando.empresaId())
                              + " -> " + resultado.totalVisitas() + " visitas"))
                .describiendoFallo(comando ->
                        "Intento de generar el reporte de visitas del "
                      + comando.desde() + " al " + comando.hasta())
                .construir();
    }

    // ==================================================================
    //  PERSONAS
    // ==================================================================

    public CasoDeUso<ComandoGuardarPersona, ResultadoPersona> guardarPersona() {
        return decoradores
                .envolver(new GuardarPersonaService(PERSONAS, TRANSACCIONES))
                .conPermiso("crear_persona")
                .auditando("GUARDAR_PERSONA", "personas",
                        (comando, resultado) -> DescripcionAuditoria.de(
                                resultado.personaId(),
                                (comando.esAlta() ? "Alta de " : "Edicion de ")
                              + comando.nombre() + " (" + comando.documentoIdentidad() + ")"))
                .describiendoFallo(comando ->
                        "Intento de guardar la persona " + comando.documentoIdentidad())
                .construir();
    }

    /**
     * Bloquear y desbloquear comparten permiso y caso de uso porque son la
     * misma decision con distinto signo. Lo que NO comparten es el efecto, y
     * por eso el detalle de la bitacora distingue ambos casos.
     */
    public CasoDeUso<ComandoCambiarEstadoAcceso, ResultadoPersona> cambiarEstadoAcceso() {
        return decoradores
                .envolver(new CambiarEstadoAccesoService(PERSONAS, TRANSACCIONES))
                .conPermiso("bloquear_persona")
                .auditando("CAMBIAR_ESTADO_ACCESO", "personas",
                        (comando, resultado) -> DescripcionAuditoria.de(
                                comando.personaId(),
                                (comando.bloquear()
                                        ? "Restriccion de ingreso IMPUESTA a "
                                        : "Restriccion de ingreso LEVANTADA a ")
                              + resultado.nombre() + " | Motivo: " + comando.motivo()))
                .describiendoFallo(comando ->
                        "Intento de " + (comando.bloquear() ? "bloquear" : "desbloquear")
                      + " a la persona #" + comando.personaId())
                .construir();
    }

    public CasoDeUso<ComandoEliminarPersona, ResultadoPersona> eliminarPersona() {
        return decoradores
                .envolver(new EliminarPersonaService(PERSONAS, TRANSACCIONES))
                .conPermiso("eliminar_persona")
                .auditando("ELIMINAR_PERSONA", "personas",
                        (comando, resultado) -> DescripcionAuditoria.de(
                                comando.personaId(),
                                "Persona eliminada: " + resultado.nombre()))
                .describiendoFallo(comando ->
                        "Intento de eliminar la persona #" + comando.personaId())
                .construir();
    }
}
