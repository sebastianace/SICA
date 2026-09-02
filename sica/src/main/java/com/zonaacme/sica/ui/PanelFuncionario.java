package com.zonaacme.sica.ui;

import com.zonaacme.sica.acceso.aplicacion.ComandoResolverSolicitud;
import com.zonaacme.sica.acceso.aplicacion.ResultadoResolucion;
import com.zonaacme.sica.acceso.aplicacion.SolicitudPendiente;
import com.zonaacme.sica.acceso.dominio.SolicitudDeIngresoCreada;
import com.zonaacme.sica.arranque.ContextoAplicacion;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/**
 * Pantalla del FUNCIONARIO DE EMPRESA: la bandeja de solicitudes que esperan
 * su visto bueno.
 *
 * Es la contraparte de PanelGuarda y el otro extremo de los flujos 2 y 3.
 *
 * Lo que la distingue de una bandeja cualquiera es que NO consulta la base de
 * datos en un ciclo. Se suscribe al bus de eventos y reacciona a
 * SolicitudDeIngresoCreada. Cuando un guarda registra a alguien en la porteria,
 * la fila aparece aqui sola. Eso es lo que el enunciado llama tiempo real.
 *
 * El reloj de espera de cada solicitud si se refresca por temporizador, pero eso
 * es distinto: no consulta nada, solo vuelve a pintar un texto que ya esta en
 * memoria. Es cosmetico, no una sondeo a la base de datos.
 */
public final class PanelFuncionario {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ContextoAplicacion contexto;
    private final UsuarioAutenticado funcionario;

    private final VBox  listaSolicitudes = new VBox(12);
    private final Label contador         = new Label("0");
    private final Label mensaje          = new Label();
    private final Label rotuloVacio      = new Label();

    private Timeline relojDeEspera;

    public PanelFuncionario(ContextoAplicacion contexto) {
        this.contexto    = contexto;
        this.funcionario = contexto.sesion().obligatorio();
    }

    public void mostrarEn(Stage escenario) {
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("cuerpo");
        raiz.setTop(barraSuperior());
        raiz.setCenter(cuerpo());

        Scene escena = new Scene(raiz, 1180, 780);
        escena.getStylesheets().add(
                getClass().getResource("/css/sica.css").toExternalForm());

        escenario.setScene(escena);
        escenario.setTitle("SICA — Bandeja de autorizaciones");
        escenario.setMinWidth(1000);
        escenario.setMinHeight(680);
        escenario.centerOnScreen();

        suscribirseAlBus();
        arrancarRelojDeEspera();
        refrescarBandeja();

        // Sin esto los hilos del temporizador y del bus sobreviven a la ventana.
        escenario.setOnCloseRequest(evento -> detener());
    }

    // ---------------------------------------------------------------------
    // TIEMPO REAL
    // ---------------------------------------------------------------------

    /**
     * La suscripcion es la pieza central de esta pantalla.
     *
     * El manejador corre en un hilo del bus, no en el de JavaFX, asi que todo
     * lo que toque la interfaz va dentro de Platform.runLater. Modificar nodos
     * desde otro hilo es el error clasico de JavaFX y no siempre falla de
     * inmediato: falla de forma intermitente, que es peor.
     */
    private void suscribirseAlBus() {
        contexto.bus().suscribir(SolicitudDeIngresoCreada.class, evento -> {

            // Cada funcionario solo debe ver lo suyo. El bus es global; el
            // filtro por empresa es lo que hace que la bandeja sea privada.
            if (funcionario.empresaId() == null
                    || evento.empresaDestinoId() != funcionario.empresaId()) {
                return;
            }

            Platform.runLater(() -> {
                mostrarMensaje("Nueva solicitud en porteria: " + evento.nombrePersona(),
                               "aviso-advertencia");
                refrescarBandeja();
            });
        });
    }

    /**
     * Repinta cada 30 s para que el "hace N min" de cada tarjeta no se congele.
     * No consulta la base de datos: solo vuelve a renderizar lo que ya se tiene.
     */
    private void arrancarRelojDeEspera() {
        relojDeEspera = new Timeline(
                new KeyFrame(Duration.seconds(30), evento -> refrescarBandeja()));
        relojDeEspera.setCycleCount(Timeline.INDEFINITE);
        relojDeEspera.play();
    }

    private void detener() {
        if (relojDeEspera != null) {
            relojDeEspera.stop();
        }
    }

    // ---------------------------------------------------------------------
    // ESTRUCTURA
    // ---------------------------------------------------------------------

    private HBox barraSuperior() {
        Label marca = Componentes.con(Componentes.espaciada("SICA"), "titulo");
        Label sub   = Componentes.con("BANDEJA DE AUTORIZACIONES", "microtexto");

        VBox identidad = new VBox(2, marca, sub);
        identidad.setAlignment(Pos.CENTER_LEFT);

        Label quien = Componentes.con(funcionario.nombreCompleto(), "subtitulo");
        Label rol   = Componentes.con(funcionario.rol(), "insignia", "insignia-rol");
        VBox  datos = new VBox(2, quien, rol);
        datos.setAlignment(Pos.CENTER_RIGHT);

        Button salir = new Button("Cerrar sesion");
        salir.getStyleClass().add("boton-secundario");
        salir.setOnAction(evento -> volverAlLogin());

        HBox barra = new HBox(18, identidad, Componentes.espaciadorHorizontal(), datos, salir);
        barra.getStyleClass().add("barra-superior");
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(16, 28, 16, 28));
        return barra;
    }

    private void volverAlLogin() {
        detener();
        Stage escenario = (Stage) listaSolicitudes.getScene().getWindow();
        contexto.sesion().cerrar();
        new VentanaLogin(contexto).mostrarEn(escenario);
    }

    private VBox cuerpo() {
        Label titulo = Componentes.con("Solicitudes pendientes", "titulo");

        contador.getStyleClass().addAll("palabra-veredicto", "texto-ambar");
        Label etiquetaContador = Componentes.con("EN ESPERA", "microtexto");
        VBox cajaContador = new VBox(0, contador, etiquetaContador);
        cajaContador.setAlignment(Pos.CENTER_RIGHT);

        HBox encabezado = new HBox(16, titulo, Componentes.espaciadorHorizontal(), cajaContador);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        Label ayuda = Componentes.con(
                "Estas solicitudes llegan solas desde la porteria. Cada persona que aparece "
              + "aqui esta esperando de pie en la entrada, asi que el tiempo importa.",
                "apagado");
        ayuda.setWrapText(true);

        rotuloVacio.getStyleClass().add("apagado");
        rotuloVacio.setText("No hay solicitudes esperando respuesta.");
        rotuloVacio.setWrapText(true);

        listaSolicitudes.setPadding(new Insets(4, 4, 4, 0));

        ScrollPane desplazable = new ScrollPane(listaSolicitudes);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("panel-hundido");
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        mensaje.getStyleClass().add("aviso");
        mensaje.setWrapText(true);
        mensaje.setVisible(false);
        mensaje.setManaged(false);

        VBox cuerpo = new VBox(18, encabezado, ayuda, mensaje, rotuloVacio, desplazable);
        cuerpo.getStyleClass().add("panel");
        cuerpo.setPadding(new Insets(28, 32, 28, 32));
        return cuerpo;
    }

    // ---------------------------------------------------------------------
    // BANDEJA
    // ---------------------------------------------------------------------

    private void refrescarBandeja() {
        if (funcionario.empresaId() == null) {
            rotuloVacio.setText("Tu usuario no tiene una empresa asignada, "
                              + "asi que no puede recibir solicitudes.");
            return;
        }

        enSegundoPlano(
                () -> contexto.visitas().pendientesDeEmpresa(funcionario.empresaId()),
                this::pintarBandeja);
    }

    private void pintarBandeja(List<SolicitudPendiente> pendientes) {
        listaSolicitudes.getChildren().clear();
        contador.setText(String.valueOf(pendientes.size()));

        boolean vacia = pendientes.isEmpty();
        rotuloVacio.setVisible(vacia);
        rotuloVacio.setManaged(vacia);

        pendientes.stream()
                  .map(this::tarjetaDeSolicitud)
                  .forEach(listaSolicitudes.getChildren()::add);
    }

    private VBox tarjetaDeSolicitud(SolicitudPendiente solicitud) {

        StackPane foto = Componentes.foto(solicitud.fotoUrl(), solicitud.nombrePersona(), 64);

        Label nombre = Componentes.con(solicitud.nombrePersona(), "subtitulo");
        Label doc    = Componentes.con(solicitud.documento(), "mono");

        Label motivo = Componentes.con(
                solicitud.motivo() == null || solicitud.motivo().isBlank()
                        ? "Sin motivo indicado."
                        : solicitud.motivo(),
                "apagado");
        motivo.setWrapText(true);

        // El tipo de visita distingue el flujo 2 del flujo 3 sin necesidad de
        // dos pantallas: al funcionario le cambia el contexto de la decision,
        // no la accion.
        boolean esOlvido = solicitud.tipo() == com.zonaacme.sica.acceso.dominio.TipoVisita.OLVIDO_CARNET;
        Label etiquetaTipo = Componentes.con(
                esOlvido ? "TRABAJADOR SIN CARNET" : "INVITADO NO ANUNCIADO",
                "insignia", esOlvido ? "insignia-alerta" : "insignia-neutra");

        Label espera = Componentes.con(tiempoEsperando(solicitud.solicitadaEn()), "microtexto");

        VBox datos = new VBox(4, nombre, doc, etiquetaTipo, motivo, espera);
        datos.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(datos, Priority.ALWAYS);

        Button aprobar = new Button("Autorizar ingreso");
        aprobar.getStyleClass().addAll("boton-primario", "boton-ingreso");
        aprobar.setOnAction(evento -> resolver(solicitud, true, null));

        Button rechazar = new Button("Rechazar");
        rechazar.getStyleClass().addAll("boton-secundario");
        rechazar.setOnAction(evento -> pedirMotivoYRechazar(solicitud));

        VBox acciones = new VBox(8, aprobar, rechazar);
        acciones.setAlignment(Pos.CENTER);

        HBox fila = new HBox(18, foto, datos, acciones);
        fila.setAlignment(Pos.CENTER_LEFT);

        VBox tarjeta = new VBox(fila);
        tarjeta.getStyleClass().addAll("panel-hundido");
        tarjeta.setPadding(new Insets(16, 18, 16, 18));
        return tarjeta;
    }

    /**
     * La antiguedad en palabras. Un funcionario decide distinto si sabe que la
     * persona lleva 20 minutos de pie en la porteria que si solo ve una hora.
     */
    private String tiempoEsperando(LocalDateTime desde) {
        if (desde == null) {
            return "Esperando respuesta";
        }
        long minutos = java.time.Duration.between(desde, LocalDateTime.now()).toMinutes();

        String cuanto = minutos < 1  ? "hace menos de un minuto"
                      : minutos < 60 ? "hace " + minutos + " min"
                                     : "hace " + (minutos / 60) + " h " + (minutos % 60) + " min";

        return "Solicitado a las " + desde.format(HORA) + " · esperando " + cuanto;
    }

    private void pedirMotivoYRechazar(SolicitudPendiente solicitud) {
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle("Rechazar solicitud");
        dialogo.setHeaderText("Rechazar el ingreso de " + solicitud.nombrePersona());
        dialogo.setContentText("Motivo:");
        dialogo.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/sica.css").toExternalForm());

        // El motivo queda en la bitacora. Si manana alguien pregunta por que no
        // se dejo entrar a esta persona, la respuesta tiene que estar escrita.
        dialogo.showAndWait().ifPresent(motivo -> resolver(solicitud, false, motivo));
    }

    private void resolver(SolicitudPendiente solicitud, boolean aprobar, String observaciones) {
        ComandoResolverSolicitud comando = aprobar
                ? ComandoResolverSolicitud.aprobar(solicitud.visitaId())
                : ComandoResolverSolicitud.rechazar(solicitud.visitaId(), observaciones);

        enSegundoPlano(
                () -> contexto.resolverSolicitud().ejecutar(comando),
                (ResultadoResolucion resultado) -> {
                    mostrarMensaje(resultado.mensaje(),
                                   resultado.aprobada() ? "aviso-exito" : "aviso-advertencia");
                    refrescarBandeja();
                });
    }

    // ---------------------------------------------------------------------
    // UTILIDADES
    // ---------------------------------------------------------------------

    private void mostrarMensaje(String texto, String clase) {
        mensaje.setText(texto);
        mensaje.getStyleClass().removeAll("aviso-exito", "aviso-error", "aviso-advertencia");
        mensaje.getStyleClass().add(clase);
        mensaje.setVisible(true);
        mensaje.setManaged(true);
    }

    private <T> void enSegundoPlano(Supplier<T> trabajo, java.util.function.Consumer<T> alTerminar) {
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() {
                return trabajo.get();
            }
        };

        tarea.setOnSucceeded(evento ->
                Platform.runLater(() -> alTerminar.accept(tarea.getValue())));

        tarea.setOnFailed(evento -> Platform.runLater(() -> {
            Throwable causa = tarea.getException();
            mostrarMensaje(causa instanceof ExcepcionDominio
                    ? causa.getMessage()
                    : "Ocurrio un error inesperado: " + causa.getMessage(), "aviso-error");
            // Si fallo porque otro funcionario respondio primero, la bandeja
            // debe reflejarlo de inmediato.
            refrescarBandeja();
        }));

        Thread hilo = new Thread(tarea, "sica-bandeja");
        hilo.setDaemon(true);
        hilo.start();
    }
}
