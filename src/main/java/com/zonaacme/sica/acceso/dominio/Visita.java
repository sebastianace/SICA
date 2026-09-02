package com.zonaacme.sica.acceso.dominio;

import com.zonaacme.sica.shared.dominio.ExcepcionDominio;

import java.time.LocalDateTime;

/**
 * Entidad central del sistema. Una instancia = un ciclo de acceso al complejo.
 *
 * La clase NO tiene setters publicos. Cada cambio de estado pasa por un metodo
 * con nombre de negocio (registrarIngreso, aprobar, cerrarPorSalidaOlvidada) y
 * cada uno consulta primero a EstadoVisita si la transicion es legal.
 * Un objeto Visita no puede quedar en un estado incoherente.
 */
public final class Visita {

    private Long id;
    private final long personaId;
    private final long empresaDestinoId;
    private Long anfitrionUsuarioId;
    private final TipoVisita tipo;
    private EstadoVisita estado;
    private final String motivo;

    private LocalDateTime fechaProgramada;
    private LocalDateTime fechaIngreso;
    private LocalDateTime fechaSalida;

    private final Long registradaPorUsuarioId;
    private Long aprobadaPorUsuarioId;
    private LocalDateTime fechaAprobacion;
    private String observaciones;

    private Visita(Long id, long personaId, long empresaDestinoId, Long anfitrionUsuarioId,
                   TipoVisita tipo, EstadoVisita estado, String motivo,
                   LocalDateTime fechaProgramada, LocalDateTime fechaIngreso,
                   LocalDateTime fechaSalida, Long registradaPorUsuarioId,
                   Long aprobadaPorUsuarioId, LocalDateTime fechaAprobacion, String observaciones) {
        this.id = id;
        this.personaId = personaId;
        this.empresaDestinoId = empresaDestinoId;
        this.anfitrionUsuarioId = anfitrionUsuarioId;
        this.tipo = tipo;
        this.estado = estado;
        this.motivo = motivo;
        this.fechaProgramada = fechaProgramada;
        this.fechaIngreso = fechaIngreso;
        this.fechaSalida = fechaSalida;
        this.registradaPorUsuarioId = registradaPorUsuarioId;
        this.aprobadaPorUsuarioId = aprobadaPorUsuarioId;
        this.fechaAprobacion = fechaAprobacion;
        this.observaciones = observaciones;
    }

    // ---------------------------------------------------------------------
    // FABRICAS: una por cada flujo del enunciado.
    // El estado inicial no se pasa como parametro, lo decide el TipoVisita.
    // ---------------------------------------------------------------------

    /** Flujo 1: el funcionario pre-registra al invitado con fecha y hora. */
    public static Visita preRegistrada(long personaId, long empresaId, long anfitrionId,
                                       String motivo, LocalDateTime fechaProgramada,
                                       long registradaPor) {
        return new Visita(null, personaId, empresaId, anfitrionId,
                TipoVisita.PRE_REGISTRADA, TipoVisita.PRE_REGISTRADA.estadoInicial(),
                motivo, fechaProgramada, null, null, registradaPor,
                anfitrionId, LocalDateTime.now(), null);
    }

    /** Flujo 2: el invitado llega sin aviso y el guarda lo registra. */
    public static Visita noAnunciada(long personaId, long empresaId, long anfitrionId,
                                     String motivo, long registradaPor) {
        return new Visita(null, personaId, empresaId, anfitrionId,
                TipoVisita.NO_ANUNCIADA, TipoVisita.NO_ANUNCIADA.estadoInicial(),
                motivo, null, null, null, registradaPor, null, null, null);
    }

    /** Flujo 3: el trabajador llega sin su carnet. */
    public static Visita porOlvidoDeCarnet(long personaId, long empresaId, long anfitrionId,
                                           long registradaPor) {
        return new Visita(null, personaId, empresaId, anfitrionId,
                TipoVisita.OLVIDO_CARNET, TipoVisita.OLVIDO_CARNET.estadoInicial(),
                "Ingreso sin carnet, requiere autorizacion del anfitrion",
                null, null, null, registradaPor, null, null, null);
    }

    /** Trabajador identificado correctamente: entra de una vez, sin aprobacion. */
    public static Visita rutinaDeTrabajador(long personaId, long empresaId, long registradaPor) {
        return new Visita(null, personaId, empresaId, null,
                TipoVisita.RUTINA_TRABAJADOR, TipoVisita.RUTINA_TRABAJADOR.estadoInicial(),
                "Jornada laboral", null, LocalDateTime.now(), null,
                registradaPor, null, null, null);
    }

    /** Reconstruccion desde la base de datos. La usa solo el adaptador JDBC. */
    public static Visita rehidratar(Long id, long personaId, long empresaDestinoId,
                                    Long anfitrionUsuarioId, TipoVisita tipo, EstadoVisita estado,
                                    String motivo, LocalDateTime fechaProgramada,
                                    LocalDateTime fechaIngreso, LocalDateTime fechaSalida,
                                    Long registradaPorUsuarioId, Long aprobadaPorUsuarioId,
                                    LocalDateTime fechaAprobacion, String observaciones) {
        return new Visita(id, personaId, empresaDestinoId, anfitrionUsuarioId, tipo, estado,
                motivo, fechaProgramada, fechaIngreso, fechaSalida, registradaPorUsuarioId,
                aprobadaPorUsuarioId, fechaAprobacion, observaciones);
    }

    // ---------------------------------------------------------------------
    // COMPORTAMIENTO
    // ---------------------------------------------------------------------

    /** Check-in: la persona cruza la porteria. APROBADA -> DENTRO. */
    public void registrarIngreso() {
        estado.validarTransicionA(EstadoVisita.DENTRO);
        this.estado = EstadoVisita.DENTRO;
        this.fechaIngreso = LocalDateTime.now();
    }

    /** Check-out normal. DENTRO -> FINALIZADA. */
    public void registrarSalida() {
        estado.validarTransicionA(EstadoVisita.FINALIZADA);
        this.estado = EstadoVisita.FINALIZADA;
        this.fechaSalida = LocalDateTime.now();
    }

    /**
     * FLUJO 4. La persona nunca registro su salida y vuelve a aparecer en la
     * porteria. No se le bloquea el ingreso, pero la visita vieja se cierra
     * dejando constancia de la inconsistencia para auditoria.
     */
    public void cerrarPorSalidaOlvidada() {
        estado.validarTransicionA(EstadoVisita.CERRADA_POR_SISTEMA);
        this.estado = EstadoVisita.CERRADA_POR_SISTEMA;
        this.fechaSalida = LocalDateTime.now();
        this.observaciones = "Cerrada automaticamente por el sistema: se detecto un nuevo "
                + "ingreso sin que se hubiera registrado la salida anterior.";
    }

    /** El funcionario autoriza una solicitud pendiente. */
    public void aprobar(long usuarioQueAprueba) {
        if (!estado.esperaAprobacion()) {
            throw new ExcepcionDominio("Esta visita no esta esperando aprobacion.");
        }
        estado.validarTransicionA(EstadoVisita.APROBADA);
        this.estado = EstadoVisita.APROBADA;
        this.aprobadaPorUsuarioId = usuarioQueAprueba;
        this.fechaAprobacion = LocalDateTime.now();
    }

    /** El funcionario niega el ingreso. */
    public void rechazar(long usuarioQueRechaza, String motivoRechazo) {
        estado.validarTransicionA(EstadoVisita.RECHAZADA);
        this.estado = EstadoVisita.RECHAZADA;
        this.aprobadaPorUsuarioId = usuarioQueRechaza;
        this.fechaAprobacion = LocalDateTime.now();
        this.observaciones = motivoRechazo;
    }

    public void asignarId(Long id) {
        if (this.id != null) {
            throw new ExcepcionDominio("Esta visita ya tiene identificador asignado.");
        }
        this.id = id;
    }

    // ---------------------------------------------------------------------
    // LECTURA
    // ---------------------------------------------------------------------
    public Long id()                        { return id; }
    public long personaId()                 { return personaId; }
    public long empresaDestinoId()          { return empresaDestinoId; }
    public Long anfitrionUsuarioId()        { return anfitrionUsuarioId; }
    public TipoVisita tipo()                { return tipo; }
    public EstadoVisita estado()            { return estado; }
    public String motivo()                  { return motivo; }
    public LocalDateTime fechaProgramada()  { return fechaProgramada; }
    public LocalDateTime fechaIngreso()     { return fechaIngreso; }
    public LocalDateTime fechaSalida()      { return fechaSalida; }
    public Long registradaPorUsuarioId()    { return registradaPorUsuarioId; }
    public Long aprobadaPorUsuarioId()      { return aprobadaPorUsuarioId; }
    public LocalDateTime fechaAprobacion()  { return fechaAprobacion; }
    public String observaciones()           { return observaciones; }
}
