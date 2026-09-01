package com.zonaacme.sica.ui;

import com.zonaacme.sica.acceso.aplicacion.ComandoConsultarPorteria;
import com.zonaacme.sica.acceso.aplicacion.ComandoRegistrarIngreso;
import com.zonaacme.sica.acceso.aplicacion.ComandoRegistrarSalida;
import com.zonaacme.sica.acceso.aplicacion.OcupanteActual;
import com.zonaacme.sica.acceso.aplicacion.ResultadoIngreso;
import com.zonaacme.sica.acceso.aplicacion.ResultadoSalida;
import com.zonaacme.sica.acceso.dominio.FichaPorteria;
import com.zonaacme.sica.acceso.dominio.PersonaEnPorteria;
import com.zonaacme.sica.directorio.aplicacion.AnfitrionBreve;
import com.zonaacme.sica.directorio.aplicacion.EmpresaBreve;
import com.zonaacme.sica.acceso.dominio.SolicitudDeIngresoResuelta;
import com.zonaacme.sica.acceso.dominio.Veredicto;
import com.zonaacme.sica.arranque.ContextoAplicacion;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/**
 * Puesto de control del guarda.
 *
 * La pantalla esta organizada alrededor de una sola pregunta: entra o no entra.
 * Por eso la franja de veredicto ocupa el centro visual y las acciones estan
 * justo debajo. Todo lo demas (la ficha, la ocupacion) es contexto de apoyo.
 *
 * Ninguna regla de negocio vive aqui. Esta clase traduce clics en comandos y
 * pinta lo que el dominio respondio. Si te encuentras escribiendo un 'if' sobre
 * estados de visita en este archivo, la regla esta en el lugar equivocado.
 */
public final class PanelGuarda {

    private static final DateTimeFormatter RELOJ = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter HORA_CORTA = DateTimeFormatter.ofPattern("HH:mm");

    private final ContextoAplicacion contexto;
    private final UsuarioAutenticado guarda;

    // Busqueda
    private final ComboBox<String> tipoDocumento = new ComboBox<>();
    private final TextField numeroDocumento = new TextField();
    private final Button botonConsultar = new Button("Consultar");

    // Franja de veredicto
    private final VBox franja = new VBox(6);
    private final Label palabraVeredicto = new Label();
    private final Label instruccionVeredicto = new Label();

    // Ficha
    private final VBox fichaPersona = new VBox(14);

    // Acciones
    private final Button botonIngreso = new Button("Registrar ingreso");
    private final Button botonSalida = new Button("Registrar salida");
    private final Label mensaje = new Label();

    // Ocupacion
    private final Label contadorOcupacion = new Label("0");
    private final VBox listaOcupacion = new VBox(8);

    private FichaPorteria fichaActual;

    public PanelGuarda(ContextoAplicacion contexto) {
        this.contexto = contexto;
        this.guarda = contexto.sesion().obligatorio();
    }

    public void mostrarEn(Stage escenario) {
        BorderPane raiz = new BorderPane();
        raiz.setTop(barraSuperior());
        raiz.setCenter(cuerpo());

        Scene escena = new Scene(raiz, 1240, 780);
        escena.getStylesheets().add(getClass().getResource("/css/sica.css").toExternalForm());

        escenario.setTitle("SICA - Porteria " + contexto.sesion().terminal());
        escenario.setScene(escena);
        escenario.setMinWidth(1100);
        escenario.setMinHeight(700);
        escenario.centerOnScreen();

        limpiarVeredicto();
        suscribirseAlBus();
        refrescarOcupacion();
        numeroDocumento.requestFocus();
    }

    /**
     * El otro extremo del flujo 2. El guarda mando la solicitud y la persona
     * quedo esperando de pie en la entrada; cuando el funcionario responde, la
     * franja de veredicto tiene que cambiar sola.
     *
     * Sin esta suscripcion el guarda tendria que estar consultando el documento
     * una y otra vez para saber si ya le respondieron, que es justo el cuello
     * de botella que el enunciado describe con el radio.
     *
     * El manejador corre en un hilo del bus, no en el de JavaFX: por eso todo
     * lo que toca la interfaz va dentro de Platform.runLater.
     */
    private void suscribirseAlBus() {
        contexto.bus().suscribir(SolicitudDeIngresoResuelta.class, evento -> Platform.runLater(() -> {

            // Solo interesa si es la persona que el guarda tiene enfrente.
            boolean esQuienEstaEnPantalla = fichaActual != null
                    && fichaActual.hayPersona()
                    && fichaActual.persona().id() == evento.personaId();

            if (!esQuienEstaEnPantalla) {
                return;
            }

            mostrarMensaje(evento.fueAprobada()
                    ? evento.resueltaPor() + " autorizo el ingreso. Ya puedes dejar pasar."
                    : evento.resueltaPor() + " rechazo el ingreso."
                      + (evento.observaciones() == null ? "" : " Motivo: " + evento.observaciones()),
                    evento.fueAprobada() ? "aviso-exito" : "aviso-error");

            // Se vuelve a consultar para que la franja y los botones se
            // recalculen desde el dominio, no desde una suposicion de la UI.
            consultar();
        }));
    }

    // ==================================================================
    // BARRA SUPERIOR
    // ==================================================================
    private HBox barraSuperior() {
        Label marca = Componentes.con("SICA", "subtitulo");
        marca.setStyle("-fx-font-size: 19px; -fx-font-weight: 800;");

        Label terminal = Componentes.con(contexto.sesion().terminal(), "mono", "apagado");

        Label nombre = Componentes.con(guarda.nombreCompleto(), "cuerpo");
        Label rol = Componentes.con(guarda.rol(), "insignia", "insignia-rol");

        Label reloj = Componentes.con("", "mono", "subtitulo");
        Timeline latido = new Timeline(new KeyFrame(Duration.seconds(1),
                evento -> reloj.setText(RELOJ.format(LocalDateTime.now()))));
        latido.setCycleCount(Animation.INDEFINITE);
        latido.play();
        reloj.setText(RELOJ.format(LocalDateTime.now()));

        Button cerrarSesion = new Button("Cerrar sesion");
        cerrarSesion.getStyleClass().add("boton-plano");
        cerrarSesion.setOnAction(evento -> volverAlLogin());

        HBox barra = new HBox(14, marca, terminal, Componentes.espaciadorHorizontal(),
                nombre, rol, reloj, cerrarSesion);
        barra.getStyleClass().add("barra-superior");
        barra.setPadding(new Insets(14, 22, 14, 22));
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void volverAlLogin() {
        contexto.sesion().cerrar();
        Stage escenario = (Stage) botonConsultar.getScene().getWindow();
        new VentanaLogin(new ContextoAplicacion()).mostrarEn(escenario);
    }

    // ==================================================================
    // CUERPO
    // ==================================================================
    private HBox cuerpo() {
        VBox columnaPrincipal = new VBox(16,
                panelBusqueda(), franjaVeredicto(), panelFicha(), panelAcciones(), mensaje);
        columnaPrincipal.setPadding(new Insets(20));
        HBox.setHgrow(columnaPrincipal, Priority.ALWAYS);

        mensaje.setWrapText(true);
        mensaje.setMaxWidth(Double.MAX_VALUE);
        ocultar(mensaje);

        ScrollPane desplazable = new ScrollPane(columnaPrincipal);
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox.setHgrow(desplazable, Priority.ALWAYS);

        return new HBox(desplazable, panelOcupacion());
    }

    private VBox panelBusqueda() {
        tipoDocumento.getItems().addAll("CC", "CE", "TI", "PASAPORTE", "PEP");
        tipoDocumento.setValue("CC");
        tipoDocumento.setPrefWidth(130);

        numeroDocumento.setPromptText("Numero de documento");
        numeroDocumento.getStyleClass().add("campo-documento");
        HBox.setHgrow(numeroDocumento, Priority.ALWAYS);
        // Enter consulta directamente: en la caseta no se usa el mouse.
        numeroDocumento.setOnAction(evento -> consultar());

        botonConsultar.getStyleClass().add("boton-primario");
        botonConsultar.setOnAction(evento -> consultar());

        Button limpiar = new Button("Limpiar");
        limpiar.getStyleClass().add("boton-secundario");
        limpiar.setOnAction(evento -> {
            numeroDocumento.clear();
            limpiarVeredicto();
            numeroDocumento.requestFocus();
        });

        HBox fila = new HBox(10, tipoDocumento, numeroDocumento, botonConsultar, limpiar);
        fila.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(10, Componentes.rotulo("Identificacion en porteria"), fila);
        panel.getStyleClass().add("panel");
        panel.setPadding(new Insets(18));
        return panel;
    }

    /**
     * LA FRANJA DE VEREDICTO.
     *
     * Una palabra, enorme, del color del semaforo. El guarda tiene que poder
     * leerla de reojo mientras mira a la persona a la cara, no a la pantalla.
     * El color viene del dominio (Veredicto.colorCss), no se decide aqui: la
     * interfaz no opina sobre quien entra.
     */
    private VBox franjaVeredicto() {
        palabraVeredicto.getStyleClass().add("palabra-veredicto");
        instruccionVeredicto.getStyleClass().add("instruccion-veredicto");
        instruccionVeredicto.setWrapText(true);

        franja.getChildren().addAll(palabraVeredicto, instruccionVeredicto);
        franja.getStyleClass().add("franja-veredicto");
        franja.setMaxWidth(Double.MAX_VALUE);
        return franja;
    }

    private VBox panelFicha() {
        fichaPersona.getStyleClass().add("panel");
        fichaPersona.setPadding(new Insets(18));
        fichaPersona.setMaxWidth(Double.MAX_VALUE);
        return fichaPersona;
    }

    private HBox panelAcciones() {
        botonIngreso.getStyleClass().add("boton-ingreso");
        botonIngreso.setOnAction(evento -> accionPrincipal());

        botonSalida.getStyleClass().add("boton-salida");
        botonSalida.setOnAction(evento -> registrarSalida());

        botonIngreso.setDisable(true);
        botonSalida.setDisable(true);

        HBox acciones = new HBox(10, botonIngreso, botonSalida);
        acciones.setAlignment(Pos.CENTER_LEFT);
        return acciones;
    }

    // ==================================================================
    // PANEL DE OCUPACION
    // ==================================================================
    private VBox panelOcupacion() {
        contadorOcupacion.setStyle("-fx-font-size: 46px; -fx-font-weight: 800;");
        contadorOcupacion.getStyleClass().addAll("mono", "texto-verde");

        Button actualizar = new Button("Actualizar");
        actualizar.getStyleClass().add("boton-plano");
        actualizar.setOnAction(evento -> refrescarOcupacion());

        HBox encabezado = new HBox(Componentes.rotulo("Dentro del complejo"),
                Componentes.espaciadorHorizontal(), actualizar);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        ScrollPane desplazable = new ScrollPane(listaOcupacion);
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        VBox panel = new VBox(10, encabezado, contadorOcupacion,
                Componentes.con("personas en este momento", "apagado"), desplazable);
        panel.getStyleClass().add("panel");
        panel.setPadding(new Insets(18));
        panel.setPrefWidth(330);
        panel.setMinWidth(330);
        VBox.setVgrow(panel, Priority.ALWAYS);

        VBox contenedor = new VBox(panel);
        contenedor.setPadding(new Insets(20, 20, 20, 0));
        VBox.setVgrow(panel, Priority.ALWAYS);
        return contenedor;
    }

    private void refrescarOcupacion() {
        enSegundoPlano(() -> contexto.visitas().ocupacionActual(), ocupantes -> {
            contadorOcupacion.setText(String.valueOf(ocupantes.size()));
            listaOcupacion.getChildren().clear();
            ocupantes.forEach(ocupante -> listaOcupacion.getChildren().add(filaOcupante(ocupante)));

            if (ocupantes.isEmpty()) {
                listaOcupacion.getChildren().add(
                        Componentes.con("El complejo esta vacio.", "apagado"));
            }
        });
    }

    private VBox filaOcupante(OcupanteActual ocupante) {
        Label nombre = Componentes.con(ocupante.nombreCompleto(), "cuerpo");
        nombre.setStyle("-fx-font-weight: 600;");

        Label detalle = Componentes.con(
                ocupante.empresaDestino() + " · " + ocupante.torre(), "microtexto");

        Label tiempo = Componentes.con(
                HORA_CORTA.format(ocupante.fechaIngreso()) + "  ·  "
              + ocupante.tiempoDentroLegible() + " dentro", "mono", "microtexto");

        VBox fila = new VBox(2, nombre, detalle, tiempo);
        fila.getStyleClass().add("panel-hundido");
        fila.setPadding(new Insets(10, 12, 10, 12));
        return fila;
    }

    // ==================================================================
    // ACCIONES
    // ==================================================================
    private void consultar() {
        String numero = numeroDocumento.getText() == null ? "" : numeroDocumento.getText().trim();
        if (numero.isEmpty()) {
            mostrarMensaje("Escribe el numero de documento.", "aviso-advertencia");
            return;
        }

        ComandoConsultarPorteria comando =
                new ComandoConsultarPorteria(tipoDocumento.getValue(), numero);

        enSegundoPlano(() -> contexto.consultarPorteria().ejecutar(comando), this::pintarFicha);
    }

    private void pintarFicha(FichaPorteria ficha) {
        this.fichaActual = ficha;
        ocultar(mensaje);

        // El color y la palabra los decidio el dominio.
        aplicarColorDeFranja(ficha.veredicto().colorCss());
        palabraVeredicto.setText(Componentes.espaciada(ficha.veredicto().palabra()));
        instruccionVeredicto.setText(ficha.detalle());

        fichaPersona.getChildren().clear();

        if (!ficha.hayPersona()) {
            fichaPersona.getChildren().add(Componentes.con(
                    "Registra a la persona desde el modulo de personas antes de darle ingreso.",
                    "apagado"));
            botonIngreso.setDisable(true);
            botonSalida.setDisable(true);
            return;
        }

        fichaPersona.getChildren().add(tarjetaDePersona(ficha.persona()));

        // Aviso de salida olvidada. Informa, no bloquea: el enunciado es
        // explicito en que la inconsistencia no puede impedir el ingreso.
        if (ficha.salidaOlvidadaDetectada()) {
            Label alerta = Componentes.con(
                    "Esta persona todavia figura DENTRO"
                  + desdeCuandoFiguraDentro(ficha)
                  + ", sin salida registrada. Al darle ingreso, el sistema cerrara esa "
                  + "visita automaticamente y dejara constancia para auditoria.",
                    "aviso", "aviso-advertencia");
            alerta.setWrapText(true);
            alerta.setMaxWidth(Double.MAX_VALUE);
            fichaPersona.getChildren().add(alerta);
        }

        configurarBotones(ficha);
    }

    /**
     * Convierte la hora de la visita abierta en la frase que el guarda necesita
     * leer. "Figura dentro" es un dato; "figura dentro desde ayer 5:59 p.m., hace
     * 26 h" es una razon para actuar. Cuando la antiguedad supera el dia, esa
     * cifra es la que delata que fue un olvido y no alguien que sigue adentro.
     */
    private String desdeCuandoFiguraDentro(FichaPorteria ficha) {
        java.time.LocalDateTime ingreso = ficha.ingresoDeVisitaAbierta();
        if (ingreso == null) {
            return "";
        }
        long minutos = java.time.Duration.between(ingreso, java.time.LocalDateTime.now()).toMinutes();
        long horas = minutos / 60;

        String antiguedad = horas < 1  ? "hace " + minutos + " min"
                          : horas < 24 ? "hace " + horas + " h"
                                       : "hace " + (horas / 24) + " d " + (horas % 24) + " h";

        return " desde el "
             + ingreso.format(java.time.format.DateTimeFormatter.ofPattern("d/MM/yyyy 'a las' HH:mm"))
             + " (" + antiguedad + ")";
    }

    private void configurarBotones(FichaPorteria ficha) {
        boolean trabajadorConEmpresa = ficha.persona().esTrabajador() && ficha.persona().empresaId() != null;
        boolean puedeEntrarYa = ficha.veredicto() == Veredicto.AUTORIZADO
                && (ficha.visitaAprobada().isPresent() || trabajadorConEmpresa);

        botonIngreso.setDisable(!puedeEntrarYa);
        botonSalida.setDisable(!ficha.salidaOlvidadaDetectada());

        if (ficha.veredicto() == Veredicto.REQUIERE_AUTORIZACION) {
            // FLUJOS 2 y 3: el guarda no autoriza, solicita. El boton queda
            // habilitado porque enviar la solicitud SI es una accion suya.
            botonIngreso.setText("Solicitar autorizacion");
            botonIngreso.setDisable(false);
            mostrarMensaje("Este ingreso necesita el visto bueno del anfitrion. "
                         + "Indica a que empresa va y quien lo recibe.", "aviso-advertencia");
        } else if (ficha.veredicto() == Veredicto.PENDIENTE) {
            botonIngreso.setText("Esperando respuesta");
            botonIngreso.setDisable(true);
        } else {
            botonIngreso.setText("Registrar ingreso");
        }
    }

    private VBox tarjetaDePersona(PersonaEnPorteria persona) {
        Label nombre = Componentes.con(persona.nombreCompleto(), "titulo");
        nombre.setStyle("-fx-font-size: 22px;");

        Label documento = Componentes.con(persona.documentoFormateado(), "mono", "subtitulo");

        Label tipo = Componentes.con(persona.tipoPersona(), "insignia", "insignia-neutra");

        HBox insignias = new HBox(8, tipo);
        if (persona.bloqueada()) {
            insignias.getChildren().add(
                    Componentes.con("RESTRICCION ACTIVA", "insignia", "insignia-alerta"));
        }

        VBox datos = new VBox(6, nombre, documento, insignias);

        if (persona.empresaNombre() != null) {
            datos.getChildren().add(new VBox(2,
                    Componentes.rotulo("Empresa"),
                    Componentes.con(persona.empresaNombre(), "cuerpo")));
        }

        HBox tarjeta = new HBox(18, Componentes.foto(persona.fotoUrl(), persona.nombreCompleto(), 108), datos);
        tarjeta.setAlignment(Pos.CENTER_LEFT);

        return new VBox(tarjeta);
    }

    /**
     * El boton de ingreso hace dos cosas distintas segun el veredicto, y por eso
     * bifurca aqui y no en el manejador del boton: si la persona esta autorizada
     * se registra el ingreso, y si requiere autorizacion se abre el formulario
     * de solicitud. Para el guarda es el mismo gesto; lo que cambia es lo que el
     * dominio permite en ese momento.
     */
    private void accionPrincipal() {
        if (fichaActual != null && fichaActual.veredicto() == Veredicto.REQUIERE_AUTORIZACION) {
            abrirFormularioDeSolicitud();
        } else {
            registrarIngreso();
        }
    }

    /**
     * Formulario de los flujos 2 y 3.
     *
     * Pide empresa destino y anfitrion porque sin esos dos datos no hay a quien
     * notificar: la solicitud se quedaria sin destinatario. El motivo solo se
     * pide para el invitado no anunciado; para el trabajador sin carnet el
     * motivo ya se conoce y pedirlo seria hacerle escribir lo obvio al guarda
     * mientras la persona espera de pie en la entrada.
     */
    private void abrirFormularioDeSolicitud() {
        PersonaEnPorteria persona = fichaActual.persona();
        boolean esOlvidoDeCarnet = persona.esTrabajador();

        ComboBox<EmpresaBreve> empresas = new ComboBox<>();
        ComboBox<AnfitrionBreve> anfitriones = new ComboBox<>();
        anfitriones.setDisable(true);

        TextField motivo = new TextField();
        motivo.setPromptText("Motivo de la visita");

        // Al elegir empresa se cargan sus anfitriones. Encadenar los selectores
        // evita que el guarda mande una solicitud a alguien de otra empresa.
        empresas.valueProperty().addListener((observable, anterior, elegida) -> {
            anfitriones.getItems().clear();
            anfitriones.setDisable(elegida == null);
            if (elegida != null) {
                enSegundoPlano(() -> contexto.directorio().anfitrionesDe(elegida.id()),
                        lista -> {
                            anfitriones.getItems().setAll(lista);
                            if (lista.size() == 1) {
                                anfitriones.setValue(lista.get(0));
                            }
                        });
            }
        });

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Solicitar autorizacion");
        dialogo.setHeaderText(esOlvidoDeCarnet
                ? "Trabajador sin carnet: " + persona.nombreCompleto()
                : "Visitante no anunciado: " + persona.nombreCompleto());
        dialogo.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/sica.css").toExternalForm());

        VBox formulario = new VBox(10,
                new VBox(4, Componentes.rotulo("Empresa destino"), empresas),
                new VBox(4, Componentes.rotulo("Anfitrion"), anfitriones));

        if (!esOlvidoDeCarnet) {
            formulario.getChildren().add(new VBox(4, Componentes.rotulo("Motivo"), motivo));
        }
        formulario.setPadding(new Insets(12));
        formulario.setPrefWidth(420);

        dialogo.getDialogPane().setContent(formulario);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // La empresa del trabajador viene preseleccionada: es la suya.
        enSegundoPlano(() -> contexto.directorio().empresasActivas(), lista -> {
            empresas.getItems().setAll(lista);
            if (persona.empresaId() != null) {
                lista.stream()
                     .filter(empresa -> empresa.id() == persona.empresaId())
                     .findFirst()
                     .ifPresent(empresas::setValue);
            }
        });

        dialogo.showAndWait()
               .filter(boton -> boton == ButtonType.OK)
               .ifPresent(boton -> enviarSolicitud(
                       esOlvidoDeCarnet, empresas.getValue(),
                       anfitriones.getValue(), motivo.getText()));
    }

    private void enviarSolicitud(boolean esOlvidoDeCarnet, EmpresaBreve empresa,
                                 AnfitrionBreve anfitrion, String motivo) {
        if (empresa == null || anfitrion == null) {
            mostrarMensaje("Hay que indicar la empresa y el anfitrion: "
                         + "sin ellos la solicitud no tiene a quien llegar.", "aviso-error");
            return;
        }

        PersonaEnPorteria persona = fichaActual.persona();

        ComandoRegistrarIngreso comando = esOlvidoDeCarnet
                ? ComandoRegistrarIngreso.trabajadorSinCarnet(
                        persona.tipoDocumento(), persona.numeroDocumento(),
                        empresa.id(), anfitrion.usuarioId())
                : ComandoRegistrarIngreso.invitadoNoAnunciado(
                        persona.tipoDocumento(), persona.numeroDocumento(),
                        empresa.id(), anfitrion.usuarioId(),
                        motivo == null || motivo.isBlank()
                                ? "Visita no anunciada" : motivo.trim());

        enSegundoPlano(() -> contexto.registrarIngreso().ejecutar(comando), resultado -> {
            mostrarMensaje("Solicitud enviada a " + anfitrion.nombre()
                         + ". La respuesta llegara a esta pantalla.", "aviso-exito");
            consultar();
        });
    }

    private void registrarIngreso() {
        if (fichaActual == null || !fichaActual.hayPersona()) return;

        PersonaEnPorteria persona = fichaActual.persona();

        // Se arma el comando segun el flujo que corresponda. La decision de que
        // flujo aplica ya venia resuelta en la ficha.
        ComandoRegistrarIngreso comando = fichaActual.visitaAprobada()
                .map(visitaId -> ComandoRegistrarIngreso.checkInDeVisitaAprobada(
                        persona.tipoDocumento(), persona.numeroDocumento(), visitaId))
                .orElseGet(() -> ComandoRegistrarIngreso.rutinaDeTrabajador(
                        persona.tipoDocumento(), persona.numeroDocumento(), persona.empresaId()));

        enSegundoPlano(() -> contexto.registrarIngreso().ejecutar(comando), this::trasIngreso);
    }

    private void trasIngreso(ResultadoIngreso resultado) {
        mostrarMensaje(resultado.mensaje(),
                resultado.cerroVisitaOlvidada() ? "aviso-advertencia" : "aviso-exito");
        refrescarOcupacion();
        consultar();
    }

    private void registrarSalida() {
        if (fichaActual == null || !fichaActual.hayPersona()) return;

        PersonaEnPorteria persona = fichaActual.persona();
        ComandoRegistrarSalida comando = new ComandoRegistrarSalida(
                persona.tipoDocumento(), persona.numeroDocumento());

        enSegundoPlano(() -> contexto.registrarSalida().ejecutar(comando), (ResultadoSalida resultado) -> {
            mostrarMensaje(resultado.mensaje(), "aviso-exito");
            refrescarOcupacion();
            consultar();
        });
    }

    // ==================================================================
    // APOYO
    // ==================================================================

    /**
     * Toda consulta a MySQL sale del hilo grafico y todo cambio de interfaz
     * vuelve a el.
     *
     * Es la regla mas facil de romper en JavaFX y la mas dificil de depurar
     * cuando se rompe: tocar un control desde otro hilo a veces lanza
     * excepcion, a veces solo deja la pantalla en un estado incoherente.
     */
    private <T> void enSegundoPlano(Supplier<T> trabajo, java.util.function.Consumer<T> alTerminar) {
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() {
                return trabajo.get();
            }
        };

        tarea.setOnSucceeded(evento -> Platform.runLater(() -> alTerminar.accept(tarea.getValue())));

        tarea.setOnFailed(evento -> Platform.runLater(() -> {
            Throwable causa = tarea.getException();
            mostrarMensaje(causa instanceof ExcepcionDominio
                    ? causa.getMessage()
                    : "Ocurrio un error inesperado: " + causa.getMessage(), "aviso-error");
        }));

        Thread hilo = new Thread(tarea, "sica-porteria");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void aplicarColorDeFranja(String color) {
        franja.getStyleClass().removeAll(
                "veredicto-verde", "veredicto-ambar", "veredicto-rojo",
                "veredicto-neutro", "veredicto-vacio");
        franja.getStyleClass().add("veredicto-" + color);

        palabraVeredicto.getStyleClass().removeAll(
                "texto-verde", "texto-ambar", "texto-rojo", "texto-neutro");
        palabraVeredicto.getStyleClass().add("texto-" + color);
    }

    private void limpiarVeredicto() {
        aplicarColorDeFranja("vacio");
        palabraVeredicto.getStyleClass().add("texto-neutro");
        palabraVeredicto.setText(Componentes.espaciada("EN ESPERA"));
        instruccionVeredicto.setText("Escanea o teclea un documento para consultar.");
        fichaPersona.getChildren().clear();
        fichaPersona.getChildren().add(Componentes.con(
                "La ficha de la persona aparecera aqui.", "apagado"));
        botonIngreso.setDisable(true);
        botonSalida.setDisable(true);
        fichaActual = null;
        ocultar(mensaje);
    }

    private void mostrarMensaje(String texto, String claseAviso) {
        mensaje.getStyleClass().removeAll("aviso-exito", "aviso-error", "aviso-advertencia");
        mensaje.getStyleClass().addAll("aviso", claseAviso);
        mensaje.setText(texto);
        mensaje.setVisible(true);
        mensaje.setManaged(true);
    }

    private void ocultar(Label etiqueta) {
        etiqueta.setVisible(false);
        etiqueta.setManaged(false);
    }
}
