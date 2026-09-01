package com.zonaacme.sica.ui;

import com.zonaacme.sica.arranque.ContextoAplicacion;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;
import com.zonaacme.sica.usuarios.aplicacion.ComandoLogin;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Pantalla de autenticacion.
 *
 * Dividida en dos: a la izquierda la identidad del sistema con el reloj y el
 * identificador de la terminal, a la derecha el formulario. La franja de la
 * izquierda no es adorno: en una porteria con varias casetas, saber en cual
 * estas parado importa, y el guarda lo ve antes de teclear nada.
 */
public final class VentanaLogin {

    private static final DateTimeFormatter RELOJ = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, yyyy");

    private final ContextoAplicacion contexto;

    private final TextField campoUsuario = new TextField();
    private final PasswordField campoClave = new PasswordField();
    private final Button botonEntrar = new Button("Iniciar sesion");
    private final Label aviso = new Label();

    public VentanaLogin(ContextoAplicacion contexto) {
        this.contexto = contexto;
    }

    public void mostrarEn(Stage escenario) {
        HBox raiz = new HBox(panelDeMarca(), panelDeFormulario());

        Scene escena = new Scene(raiz, 940, 560);
        escena.getStylesheets().add(getClass().getResource("/css/sica.css").toExternalForm());

        escenario.setTitle("SICA - Zona Acme");
        escenario.setScene(escena);
        escenario.setMinWidth(860);
        escenario.setMinHeight(520);
        escenario.show();

        campoUsuario.requestFocus();
    }

    // ------------------------------------------------------------------
    private VBox panelDeMarca() {
        Label marca = Componentes.con("SICA", "titulo");
        marca.setStyle("-fx-font-size: 46px; -fx-font-weight: 800;");

        Label descripcion = Componentes.con(
                "Sistema Integrado\nde Control de Acceso", "subtitulo");
        descripcion.setStyle("-fx-font-size: 16px; -fx-font-weight: 500; -fx-line-spacing: 3px;");

        Label complejo = Componentes.con("Complejo Empresarial Zona Acme", "apagado");

        Label reloj = Componentes.con("", "mono", "titulo");
        reloj.setStyle("-fx-font-size: 30px; -fx-font-weight: 600;");
        Label fecha = Componentes.con("", "apagado");

        arrancarReloj(reloj, fecha);

        VBox pieDeTerminal = new VBox(3,
                Componentes.rotulo("Terminal"),
                Componentes.con(contexto.sesion().terminal(), "mono", "cuerpo"));

        VBox contenedor = new VBox(14,
                marca, descripcion, complejo,
                Componentes.espaciadorVertical(),
                reloj, fecha,
                new Separator(),
                pieDeTerminal);

        contenedor.getStyleClass().add("panel-marca");
        contenedor.setPadding(new Insets(46, 38, 38, 42));
        contenedor.setPrefWidth(360);
        contenedor.setMinWidth(360);
        return contenedor;
    }

    private void arrancarReloj(Label reloj, Label fecha) {
        Runnable actualizar = () -> {
            LocalDateTime ahora = LocalDateTime.now();
            reloj.setText(RELOJ.format(ahora));
            fecha.setText(capitalizar(FECHA.format(ahora)));
        };
        actualizar.run();

        Timeline latido = new Timeline(
                new KeyFrame(Duration.seconds(1), evento -> actualizar.run()));
        latido.setCycleCount(Animation.INDEFINITE);
        latido.play();
    }

    private String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    // ------------------------------------------------------------------
    private VBox panelDeFormulario() {
        Label titulo = Componentes.con("Iniciar sesion", "titulo");
        Label ayuda = Componentes.con(
                "Identificate para operar el control de acceso.", "apagado");

        campoUsuario.setPromptText("correo@empresa.co");
        campoClave.setPromptText("contrasena");

        campoUsuario.setOnAction(evento -> campoClave.requestFocus());
        campoClave.setOnAction(evento -> intentarLogin());

        botonEntrar.getStyleClass().add("boton-primario");
        botonEntrar.setMaxWidth(Double.MAX_VALUE);
        botonEntrar.setOnAction(evento -> intentarLogin());

        aviso.getStyleClass().addAll("aviso", "aviso-error");
        aviso.setWrapText(true);
        aviso.setMaxWidth(Double.MAX_VALUE);
        aviso.setVisible(false);
        aviso.setManaged(false);

        VBox formulario = new VBox(16,
                titulo, ayuda,
                new VBox(6, Componentes.rotulo("Correo"), campoUsuario),
                new VBox(6, Componentes.rotulo("Contrasena"), campoClave),
                botonEntrar,
                aviso);

        formulario.setMaxWidth(380);
        formulario.setAlignment(Pos.CENTER_LEFT);

        VBox contenedor = new VBox(formulario);
        contenedor.setAlignment(Pos.CENTER);
        contenedor.setPadding(new Insets(40, 56, 40, 56));
        HBox.setHgrow(contenedor, Priority.ALWAYS);
        return contenedor;
    }

    // ------------------------------------------------------------------
    /**
     * La verificacion de la contrasena corre en un hilo aparte.
     *
     * PBKDF2 con 120.000 iteraciones tarda cerca de una decima de segundo a
     * proposito: esa lentitud es lo que la hace resistente a fuerza bruta.
     * Ejecutarla en el hilo de JavaFX congelaria la ventana en cada intento.
     * Task se encarga de devolver el resultado al hilo grafico, que es el unico
     * autorizado a tocar la interfaz.
     */
    private void intentarLogin() {
        String usuario = campoUsuario.getText() == null ? "" : campoUsuario.getText().trim();
        String clave = campoClave.getText() == null ? "" : campoClave.getText();

        if (usuario.isEmpty() || clave.isEmpty()) {
            mostrarAviso("Escribe tu usuario y tu contrasena.");
            return;
        }

        botonEntrar.setDisable(true);
        botonEntrar.setText("Verificando...");
        ocultarAviso();

        Task<UsuarioAutenticado> tarea = new Task<>() {
            @Override
            protected UsuarioAutenticado call() {
                return contexto.iniciarSesion().ejecutar(
                        new ComandoLogin(usuario, clave.toCharArray()));
            }
        };

        tarea.setOnSucceeded(evento -> abrirPanelPrincipal(tarea.getValue()));

        tarea.setOnFailed(evento -> {
            restaurarBoton();
            Throwable causa = tarea.getException();
            mostrarAviso(causa instanceof ExcepcionDominio
                    ? causa.getMessage()
                    : "No se pudo iniciar sesion. Revisa la conexion con la base de datos.");
            campoClave.clear();
            campoClave.requestFocus();
        });

        Thread hilo = new Thread(tarea, "sica-login");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void abrirPanelPrincipal(UsuarioAutenticado usuario) {
        restaurarBoton();
        Stage escenario = (Stage) botonEntrar.getScene().getWindow();

        // Cada rol entra por su propia puerta. La comprobacion es por permiso,
        // no por nombre de rol: un rol nuevo creado con INSERT en la base de
        // datos entra por la puerta que le corresponda sin tocar este codigo.
        if (usuario.tienePermiso("consultar_porteria") && usuario.tienePermiso("registrar_visita")) {
            new PanelGuarda(contexto).mostrarEn(escenario);
            return;
        }

        if (usuario.tienePermiso("aprobar_visita")) {
            new PanelFuncionario(contexto).mostrarEn(escenario);
            return;
        }

        // El enrutamiento es por PERMISO, no por nombre de rol. Un rol nuevo
        // creado con un INSERT entra por la puerta que le corresponda sin
        // recompilar nada.
        if (usuario.tienePermiso("generar_reporte") || usuario.tienePermiso("ver_bitacora")) {
            new PanelSupervisor(contexto).mostrarEn(escenario);
            return;
        }

        mostrarAviso("Tu rol (" + usuario.rol() + ") todavia no tiene una pantalla asignada. "
                   + "El panel de administracion se habilita en la siguiente etapa.");
        contexto.sesion().cerrar();
    }

    private void restaurarBoton() {
        botonEntrar.setDisable(false);
        botonEntrar.setText("Iniciar sesion");
    }

    private void mostrarAviso(String mensaje) {
        aviso.setText(mensaje);
        aviso.setVisible(true);
        aviso.setManaged(true);
    }

    private void ocultarAviso() {
        aviso.setVisible(false);
        aviso.setManaged(false);
    }
}
