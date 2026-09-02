package com.zonaacme.sica.ui;

import com.zonaacme.sica.arranque.ContextoAplicacion;
import com.zonaacme.sica.incidentes.aplicacion.*;
import com.zonaacme.sica.incidentes.dominio.EstadoIncidente;
import com.zonaacme.sica.incidentes.dominio.GravedadIncidente;
import com.zonaacme.sica.incidentes.dominio.TipoIncidente;
import com.zonaacme.sica.directorio.aplicacion.EmpresaBreve;
import com.zonaacme.sica.personas.aplicacion.*;
import com.zonaacme.sica.personas.dominio.TipoPersona;
import com.zonaacme.sica.reportes.aplicacion.*;
import com.zonaacme.sica.shared.auditoria.LineaBitacora;
import com.zonaacme.sica.shared.auditoria.ResultadoIntegridad;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pantalla del SUPERVISOR DE SEGURIDAD.
 *
 * Una sola ventana con tres pestañas en vez de tres ventanas. La razon es de
 * uso, no de ahorro de codigo: las tres cosas que hace un supervisor se miran
 * juntas. Se detecta un patron raro en el reporte, se busca el incidente que lo
 * explica, y se comprueba en la bitacora quien hizo que. Obligar a abrir y
 * cerrar ventanas para eso rompe el hilo de la investigacion.
 */
public final class PanelSupervisor {

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter SOLO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ContextoAplicacion contexto;
    private final UsuarioAutenticado supervisor;

    // ---- Reportes ----
    private final DatePicker desde  = new DatePicker(LocalDate.now().minusDays(29));
    private final DatePicker hasta  = new DatePicker(LocalDate.now());
    private final VBox  panelResumen = new VBox(14);
    private final Label mensajeReporte = new Label();
    private ReporteVisitas ultimoReporte;

    // ---- Personas ----
    private final TextField buscadorPersonas = new TextField();
    private final VBox  listaPersonas  = new VBox(8);
    private final Label mensajePersona = new Label();

    // ---- Incidentes ----
    private final VBox  listaIncidentes = new VBox(10);
    private final Label mensajeIncidente = new Label();

    // ---- Bitacora ----
    private final VBox  listaBitacora = new VBox(2);
    private final Label franjaIntegridad = new Label();

    public PanelSupervisor(ContextoAplicacion contexto) {
        this.contexto   = contexto;
        this.supervisor = contexto.sesion().obligatorio();
    }

    public void mostrarEn(Stage escenario) {
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("cuerpo");
        raiz.setTop(barraSuperior());

        TabPane pestanas = new TabPane();
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pestanas.getTabs().addAll(
                new Tab("Personas",   pestanaPersonas()),
                new Tab("Reportes",   pestanaReportes()),
                new Tab("Incidentes", pestanaIncidentes()),
                new Tab("Auditoria",  pestanaBitacora()));
        raiz.setCenter(pestanas);

        Scene escena = new Scene(raiz, 1240, 820);
        escena.getStylesheets().add(getClass().getResource("/css/sica.css").toExternalForm());

        escenario.setScene(escena);
        escenario.setTitle("SICA — Supervision y auditoria");
        escenario.setMinWidth(1080);
        escenario.setMinHeight(720);
        escenario.centerOnScreen();

        refrescarPersonas();
        generarReporte();
        refrescarIncidentes();
        refrescarBitacora();
    }

    // ==================================================================
    //  BARRA SUPERIOR
    // ==================================================================

    private HBox barraSuperior() {
        VBox identidad = new VBox(2,
                Componentes.con(Componentes.espaciada("SICA"), "titulo"),
                Componentes.con("SUPERVISION Y AUDITORIA", "microtexto"));

        VBox datos = new VBox(2,
                Componentes.con(supervisor.nombreCompleto(), "subtitulo"),
                Componentes.con(supervisor.rol(), "insignia", "insignia-rol"));
        datos.setAlignment(Pos.CENTER_RIGHT);

        Button salir = new Button("Cerrar sesion");
        salir.getStyleClass().add("boton-secundario");
        salir.setOnAction(evento -> {
            Stage escenario = (Stage) listaIncidentes.getScene().getWindow();
            contexto.sesion().cerrar();
            new VentanaLogin(contexto).mostrarEn(escenario);
        });

        HBox barra = new HBox(18, identidad, Componentes.espaciadorHorizontal(), datos, salir);
        barra.getStyleClass().add("barra-superior");
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(16, 28, 16, 28));
        return barra;
    }

    // ==================================================================
    //  PESTAÑA — PERSONAS
    // ==================================================================

    private VBox pestanaPersonas() {
        buscadorPersonas.setPromptText("Buscar por nombre o documento");
        buscadorPersonas.setPrefWidth(320);
        buscadorPersonas.setOnAction(evento -> refrescarPersonas());

        Button buscar = new Button("Buscar");
        buscar.getStyleClass().add("boton-secundario");
        buscar.setOnAction(evento -> refrescarPersonas());

        Button nueva = new Button("Registrar persona");
        nueva.getStyleClass().add("boton-primario");
        nueva.setOnAction(evento -> abrirFormularioPersona(null));
        // La interfaz se adapta al permiso: si el usuario no puede crear
        // personas, el boton no existe para el. Esto NO sustituye la validacion
        // del servidor: el decorador de seguridad la vuelve a hacer en el caso
        // de uso. Ocultar un boton es comodidad, no seguridad.
        soloConPermiso(nueva, "crear_persona");

        HBox acciones = new HBox(12, buscadorPersonas, buscar,
                Componentes.espaciadorHorizontal(), nueva);
        acciones.setAlignment(Pos.CENTER_LEFT);

        mensajePersona.getStyleClass().add("aviso");
        mensajePersona.setWrapText(true);
        ocultar(mensajePersona);

        ScrollPane desplazable = new ScrollPane(listaPersonas);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("panel-hundido");
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        VBox contenido = new VBox(16,
                Componentes.con("Las personas con restriccion de ingreso aparecen primero. "
                              + "Un bloqueo es efectivo de inmediato en todas las porterias.",
                                "apagado"),
                acciones, mensajePersona, desplazable);
        contenido.getStyleClass().add("panel");
        contenido.setPadding(new Insets(24, 28, 24, 28));
        return contenido;
    }

    private void refrescarPersonas() {
        enSegundoPlano(() -> contexto.personas().listar(buscadorPersonas.getText()),
                       this::pintarPersonas, mensajePersona);
    }

    private void pintarPersonas(List<Persona> personas) {
        listaPersonas.getChildren().clear();
        if (personas.isEmpty()) {
            listaPersonas.getChildren().add(
                    Componentes.con("No hay personas que coincidan.", "apagado"));
            return;
        }
        personas.stream().map(this::filaPersona)
                .forEach(listaPersonas.getChildren()::add);
    }

    private HBox filaPersona(Persona persona) {
        Label nombre = Componentes.con(persona.nombre(), "subtitulo");
        Label documento = Componentes.con(persona.documentoFormateado(), "mono", "microtexto");

        HBox insignias = new HBox(6,
                Componentes.con(persona.tipo().valorEnBd().toUpperCase(),
                                "insignia", "insignia-neutra"));

        if (persona.estaBloqueada()) {
            insignias.getChildren().add(
                    Componentes.con("RESTRINGIDO", "insignia", "insignia-alerta"));
        }

        VBox datos = new VBox(4, nombre, documento, insignias);
        if (persona.empresaNombre() != null) {
            datos.getChildren().add(
                    Componentes.con(persona.empresaNombre(), "microtexto"));
        }
        if (persona.estaBloqueada() && persona.motivoBloqueo() != null) {
            Label motivo = Componentes.con("Motivo: " + persona.motivoBloqueo(), "microtexto");
            motivo.setWrapText(true);
            datos.getChildren().add(motivo);
        }
        HBox.setHgrow(datos, Priority.ALWAYS);

        Button editar = new Button("Editar");
        editar.getStyleClass().add("boton-plano");
        editar.setOnAction(evento -> abrirFormularioPersona(persona));
        soloConPermiso(editar, "editar_persona");

        Button acceso = new Button(persona.estaBloqueada() ? "Levantar" : "Bloquear");
        acceso.getStyleClass().add(persona.estaBloqueada() ? "boton-secundario" : "boton-salida");
        acceso.setOnAction(evento -> pedirMotivoYCambiarAcceso(persona));
        soloConPermiso(acceso, "bloquear_persona");

        Button eliminar = new Button("Eliminar");
        eliminar.getStyleClass().add("boton-plano");
        eliminar.setOnAction(evento -> confirmarEliminacion(persona));
        soloConPermiso(eliminar, "eliminar_persona");

        HBox fila = new HBox(10, datos, editar, acceso, eliminar);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("panel-hundido");
        fila.setPadding(new Insets(12, 14, 12, 14));
        return fila;
    }

    /**
     * El motivo es obligatorio en ambos sentidos, tambien al levantar la
     * restriccion. Desbloquear sin dejar constancia de por que es el mismo
     * agujero de trazabilidad que bloquear sin motivo.
     */
    private void pedirMotivoYCambiarAcceso(Persona persona) {
        boolean bloquear = !persona.estaBloqueada();

        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle(bloquear ? "Imponer restriccion" : "Levantar restriccion");
        dialogo.setHeaderText((bloquear ? "Prohibir el ingreso de " : "Permitir de nuevo el ingreso de ")
                            + persona.nombre());
        dialogo.setContentText("Motivo:");
        dialogo.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/sica.css").toExternalForm());

        dialogo.showAndWait().ifPresent(motivo -> {
            try {
                var comando = new ComandoCambiarEstadoAcceso(persona.id(), bloquear, motivo);
                enSegundoPlano(() -> contexto.cambiarEstadoAcceso().ejecutar(comando),
                        resultado -> {
                            mostrar(mensajePersona, resultado.mensaje(),
                                    bloquear ? "aviso-advertencia" : "aviso-exito");
                            refrescarPersonas();
                        }, mensajePersona);
            } catch (IllegalArgumentException e) {
                mostrar(mensajePersona, e.getMessage(), "aviso-error");
            }
        });
    }

    private void confirmarEliminacion(Persona persona) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar persona");
        confirmacion.setHeaderText("Eliminar a " + persona.nombre());
        confirmacion.setContentText("Esta accion no se puede deshacer. "
                + "Si el objetivo es impedirle la entrada, es mejor imponer una restriccion.");
        confirmacion.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/sica.css").toExternalForm());

        confirmacion.showAndWait()
                .filter(boton -> boton == ButtonType.OK)
                .ifPresent(boton -> enSegundoPlano(
                        () -> contexto.eliminarPersona().ejecutar(
                                new ComandoEliminarPersona(persona.id())),
                        resultado -> {
                            mostrar(mensajePersona, resultado.mensaje(), "aviso-exito");
                            refrescarPersonas();
                        }, mensajePersona));
    }

    private void abrirFormularioPersona(Persona existente) {
        boolean esAlta = existente == null;

        TextField nombre = new TextField(esAlta ? "" : existente.nombre());
        TextField documento = new TextField(esAlta ? "" : existente.documentoIdentidad());
        TextField telefono = new TextField(esAlta || existente.telefono() == null
                ? "" : existente.telefono());
        TextField foto = new TextField(esAlta || existente.urlFoto() == null
                ? "" : existente.urlFoto());
        foto.setPromptText("https://... (opcional)");

        ComboBox<String> tipoDocumento = new ComboBox<>();
        tipoDocumento.getItems().addAll("CC", "CE", "TI", "PASAPORTE", "PEP");
        tipoDocumento.setValue(esAlta ? "CC" : existente.tipoDocumento());

        ComboBox<TipoPersona> tipo = new ComboBox<>();
        tipo.getItems().setAll(TipoPersona.values());
        tipo.setValue(esAlta ? TipoPersona.INVITADO : existente.tipo());

        ComboBox<EmpresaBreve> empresa = new ComboBox<>();
        enSegundoPlano(() -> contexto.directorio().empresasActivas(), lista -> {
            empresa.getItems().setAll(lista);
            if (!esAlta && existente.empresaId() != null) {
                lista.stream().filter(e -> e.id() == existente.empresaId())
                     .findFirst().ifPresent(empresa::setValue);
            }
        }, mensajePersona);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(esAlta ? "Registrar persona" : "Editar persona");
        dialogo.setHeaderText(esAlta ? "Nueva persona en el sistema" : existente.nombre());
        dialogo.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/sica.css").toExternalForm());

        VBox formulario = new VBox(10,
                new VBox(4, Componentes.rotulo("Nombre completo"), nombre),
                new HBox(10,
                        new VBox(4, Componentes.rotulo("Tipo doc."), tipoDocumento),
                        new VBox(4, Componentes.rotulo("Documento"), documento)),
                new VBox(4, Componentes.rotulo("Telefono"), telefono),
                new VBox(4, Componentes.rotulo("Tipo de persona"), tipo),
                new VBox(4, Componentes.rotulo("Empresa"), empresa),
                new VBox(4, Componentes.rotulo("URL de la foto"), foto));
        formulario.setPadding(new Insets(12));
        formulario.setPrefWidth(460);

        dialogo.getDialogPane().setContent(formulario);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialogo.showAndWait()
                .filter(boton -> boton == ButtonType.OK)
                .ifPresent(boton -> guardarPersona(
                        esAlta ? null : existente.id(),
                        nombre.getText(), tipoDocumento.getValue(), documento.getText(),
                        telefono.getText(), foto.getText(), tipo.getValue(),
                        empresa.getValue() == null ? null : empresa.getValue().id()));
    }

    private void guardarPersona(Long id, String nombre, String tipoDocumento,
                                String documento, String telefono, String foto,
                                TipoPersona tipo, Long empresaId) {
        ComandoGuardarPersona comando;
        try {
            comando = new ComandoGuardarPersona(id, nombre, tipoDocumento, documento,
                                                telefono, foto, tipo, empresaId);
        } catch (IllegalArgumentException e) {
            // Las validaciones del comando son del dominio, no de la pantalla:
            // asi valen igual si manana la persona se crea desde otra interfaz.
            mostrar(mensajePersona, e.getMessage(), "aviso-error");
            return;
        }

        enSegundoPlano(() -> contexto.guardarPersona().ejecutar(comando),
                resultado -> {
                    mostrar(mensajePersona, resultado.mensaje(), "aviso-exito");
                    refrescarPersonas();
                }, mensajePersona);
    }

    // ==================================================================
    //  PESTAÑA 1 — REPORTES
    // ==================================================================

    private VBox pestanaReportes() {
        Button generar = new Button("Generar");
        generar.getStyleClass().add("boton-primario");
        generar.setOnAction(evento -> generarReporte());

        Button exportar = new Button("Exportar CSV");
        exportar.getStyleClass().add("boton-secundario");
        exportar.setOnAction(evento -> exportarCsv());

        HBox filtros = new HBox(12,
                new VBox(4, Componentes.rotulo("Desde"), desde),
                new VBox(4, Componentes.rotulo("Hasta"), hasta),
                new VBox(4, Componentes.con(" ", "microtexto"), generar),
                new VBox(4, Componentes.con(" ", "microtexto"), exportar));
        filtros.setAlignment(Pos.BOTTOM_LEFT);

        mensajeReporte.getStyleClass().add("aviso");
        mensajeReporte.setWrapText(true);
        ocultar(mensajeReporte);

        ScrollPane desplazable = new ScrollPane(panelResumen);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("panel-hundido");
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        VBox contenido = new VBox(18, filtros, mensajeReporte, desplazable);
        contenido.getStyleClass().add("panel");
        contenido.setPadding(new Insets(24, 28, 24, 28));
        return contenido;
    }

    private void generarReporte() {
        ComandoReporteVisitas comando;
        try {
            comando = new ComandoReporteVisitas(desde.getValue(), hasta.getValue(), null);
        } catch (IllegalArgumentException e) {
            mostrar(mensajeReporte, e.getMessage(), "aviso-error");
            return;
        }

        enSegundoPlano(
                () -> contexto.generarReporteVisitas().ejecutar(comando),
                reporte -> {
                    ultimoReporte = reporte;
                    pintarReporte(reporte);
                },
                mensajeReporte);
    }

    private void pintarReporte(ReporteVisitas reporte) {
        panelResumen.getChildren().clear();
        ocultar(mensajeReporte);

        if (reporte.estaVacio()) {
            panelResumen.getChildren().add(
                    Componentes.con("No hubo visitas en este rango de fechas.", "apagado"));
            return;
        }

        // Fila de indicadores. El de salidas olvidadas va aparte porque no mide
        // el uso del sistema sino un problema del proceso: si sube, la gente
        // esta saliendo sin registrar salida y el tablero de ocupacion pierde
        // fiabilidad justo cuando mas importa.
        HBox indicadores = new HBox(28,
                indicador(String.valueOf(reporte.totalVisitas()),    "VISITAS",        "texto-neutro"),
                indicador(String.valueOf(reporte.totalConIngreso()), "CON INGRESO",    "texto-neutro"),
                indicador(String.valueOf(reporte.totalDentroAhora()),"DENTRO AHORA",   "texto-verde"),
                indicador(String.format("%.1f%%", reporte.porcentajeSalidasOlvidadas()),
                          "SALIDAS OLVIDADAS",
                          reporte.porcentajeSalidasOlvidadas() > 10 ? "texto-rojo" : "texto-ambar"));
        indicadores.setAlignment(Pos.CENTER_LEFT);

        panelResumen.getChildren().addAll(
                indicadores,
                bloqueConteo("Visitas por empresa",       reporte.visitasPorEmpresa()),
                bloqueConteo("Visitas por tipo",          reporte.visitasPorTipo()),
                bloqueConteo("Visitas por estado",        reporte.visitasPorEstado()),
                bloqueHoras(reporte.ingresosPorHora()),
                bloquePromedios(reporte.promedioEstanciaPorEmpresa()),
                bloqueEstanciasLargas(reporte.estanciasMasLargas()));
    }

    private VBox indicador(String valor, String etiqueta, String claseColor) {
        Label numero = Componentes.con(valor, "palabra-veredicto", claseColor);
        VBox caja = new VBox(0, numero, Componentes.con(etiqueta, "microtexto"));
        caja.setAlignment(Pos.CENTER_LEFT);
        return caja;
    }

    /** Barras proporcionales: comparar longitudes es mas rapido que leer cifras. */
    private VBox bloqueConteo(String titulo, Map<String, Long> datos) {
        VBox bloque = new VBox(6, Componentes.con(titulo, "subtitulo"));
        long maximo = datos.values().stream().mapToLong(Long::longValue).max().orElse(1);

        datos.forEach((clave, cantidad) -> {
            Region barra = new Region();
            barra.getStyleClass().add("insignia-neutra");
            barra.setMinHeight(14);
            barra.setPrefWidth(Math.max(6, (cantidad * 320.0) / maximo));

            HBox fila = new HBox(10,
                    anchoFijo(Componentes.con(clave, "apagado"), 240),
                    barra,
                    Componentes.con(String.valueOf(cantidad), "mono"));
            fila.setAlignment(Pos.CENTER_LEFT);
            bloque.getChildren().add(fila);
        });
        bloque.getStyleClass().add("panel-hundido");
        bloque.setPadding(new Insets(14));
        return bloque;
    }

    private VBox bloqueHoras(Map<Integer, Long> porHora) {
        VBox bloque = new VBox(6, Componentes.con("Ingresos por hora del dia", "subtitulo"));
        long maximo = porHora.values().stream().mapToLong(Long::longValue).max().orElse(1);

        HBox columnas = new HBox(4);
        columnas.setAlignment(Pos.BOTTOM_LEFT);
        porHora.forEach((hora, cantidad) -> {
            Region barra = new Region();
            barra.getStyleClass().add("insignia-alerta");
            barra.setMinWidth(26);
            barra.setPrefHeight(Math.max(8, (cantidad * 90.0) / maximo));

            VBox columna = new VBox(3, barra,
                    Componentes.con(String.format("%02d", hora), "microtexto"));
            columna.setAlignment(Pos.BOTTOM_CENTER);
            columnas.getChildren().add(columna);
        });

        bloque.getChildren().addAll(columnas,
                Componentes.con("La hora con la barra mas alta es cuando la porteria "
                              + "recibe mas gente.", "microtexto"));
        bloque.getStyleClass().add("panel-hundido");
        bloque.setPadding(new Insets(14));
        return bloque;
    }

    private VBox bloquePromedios(Map<String, Double> promedios) {
        VBox bloque = new VBox(6,
                Componentes.con("Estancia promedio por empresa", "subtitulo"),
                Componentes.con("Solo sobre visitas ya cerradas: incluir las abiertas haria "
                              + "que el promedio cambiara cada minuto.", "microtexto"));

        promedios.forEach((empresa, minutos) -> {
            long total = Math.round(minutos);
            String legible = total >= 60
                    ? (total / 60) + " h " + (total % 60) + " min"
                    : total + " min";
            HBox fila = new HBox(10,
                    anchoFijo(Componentes.con(empresa, "apagado"), 240),
                    Componentes.con(legible, "mono"));
            fila.setAlignment(Pos.CENTER_LEFT);
            bloque.getChildren().add(fila);
        });
        bloque.getStyleClass().add("panel-hundido");
        bloque.setPadding(new Insets(14));
        return bloque;
    }

    private VBox bloqueEstanciasLargas(List<FilaVisita> filas) {
        VBox bloque = new VBox(6,
                Componentes.con("Estancias mas largas", "subtitulo"),
                Componentes.con("Candidatas a revision manual.", "microtexto"));

        filas.forEach(fila -> {
            long minutos = fila.minutosDeEstancia().orElse(0L);
            HBox linea = new HBox(10,
                    anchoFijo(Componentes.con(fila.nombrePersona(), "apagado"), 220),
                    anchoFijo(Componentes.con(fila.empresa(), "microtexto"), 200),
                    Componentes.con((minutos / 60) + " h " + (minutos % 60) + " min", "mono"));
            linea.setAlignment(Pos.CENTER_LEFT);
            bloque.getChildren().add(linea);
        });
        bloque.getStyleClass().add("panel-hundido");
        bloque.setPadding(new Insets(14));
        return bloque;
    }

    private void exportarCsv() {
        if (ultimoReporte == null || ultimoReporte.estaVacio()) {
            mostrar(mensajeReporte, "Genera primero un reporte con datos.", "aviso-advertencia");
            return;
        }

        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar reporte");
        selector.setInitialFileName("reporte-visitas-" + LocalDate.now() + ".csv");
        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivo CSV", "*.csv"));

        java.io.File destino = selector.showSaveDialog(panelResumen.getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            // BOM UTF-8: sin el, Excel abre las tildes y las ñ como simbolos raros.
            String contenido = "\uFEFF" + GenerarReporteVisitasService.aCsv(ultimoReporte);
            Files.writeString(Path.of(destino.toURI()), contenido);
            mostrar(mensajeReporte,
                    "Reporte exportado: " + destino.getName(), "aviso-exito");
        } catch (IOException e) {
            mostrar(mensajeReporte, "No se pudo escribir el archivo: " + e.getMessage(),
                    "aviso-error");
        }
    }

    // ==================================================================
    //  PESTAÑA 2 — INCIDENTES
    // ==================================================================

    private VBox pestanaIncidentes() {
        Button nuevo = new Button("Reportar incidente");
        nuevo.getStyleClass().add("boton-primario");
        nuevo.setOnAction(evento -> abrirFormularioIncidente());

        Button refrescar = new Button("Refrescar");
        refrescar.getStyleClass().add("boton-secundario");
        refrescar.setOnAction(evento -> refrescarIncidentes());

        HBox acciones = new HBox(12, nuevo, refrescar);

        mensajeIncidente.getStyleClass().add("aviso");
        mensajeIncidente.setWrapText(true);
        ocultar(mensajeIncidente);

        ScrollPane desplazable = new ScrollPane(listaIncidentes);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("panel-hundido");
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        VBox contenido = new VBox(16,
                Componentes.con("Los incidentes sin resolver aparecen primero, "
                              + "y dentro de ellos los mas graves arriba.", "apagado"),
                acciones, mensajeIncidente, desplazable);
        contenido.getStyleClass().add("panel");
        contenido.setPadding(new Insets(24, 28, 24, 28));
        return contenido;
    }

    private void refrescarIncidentes() {
        enSegundoPlano(() -> contexto.incidentes().listarTodos(),
                       this::pintarIncidentes, mensajeIncidente);
    }

    private void pintarIncidentes(List<Incidente> incidentes) {
        listaIncidentes.getChildren().clear();
        if (incidentes.isEmpty()) {
            listaIncidentes.getChildren().add(
                    Componentes.con("No hay incidentes registrados.", "apagado"));
            return;
        }
        incidentes.stream().map(this::tarjetaIncidente)
                  .forEach(listaIncidentes.getChildren()::add);
    }

    private VBox tarjetaIncidente(Incidente incidente) {
        Label gravedad = Componentes.con(incidente.gravedad().etiqueta().toUpperCase(),
                "insignia",
                incidente.gravedad().requiereBloqueoInmediato()
                        ? "insignia-alerta" : "insignia-neutra");

        Label estado = Componentes.con(incidente.estado().name(), "insignia", "insignia-neutra");

        Label titulo = Componentes.con(incidente.tipo().etiqueta(), "subtitulo");

        String quien = incidente.nombrePersona() == null
                ? "Sin persona identificada"
                : incidente.nombrePersona() + " · " + incidente.documentoPersona();

        Label sujeto = Componentes.con(quien, "apagado");

        Label meta = Componentes.con(
                "Reportado por " + incidente.reportadoPor()
              + (incidente.fecha() == null ? "" : " · " + incidente.fecha().format(SOLO_FECHA)),
                "microtexto");

        Label descripcion = Componentes.con(incidente.descripcion(), "apagado");
        descripcion.setWrapText(true);

        HBox insignias = new HBox(8, gravedad, estado);

        VBox datos = new VBox(6, titulo, sujeto, insignias, descripcion, meta);
        HBox.setHgrow(datos, Priority.ALWAYS);

        HBox fila = new HBox(16, datos);
        if (incidente.estaAbierto()) {
            Button cerrar = new Button("Cerrar");
            cerrar.getStyleClass().add("boton-secundario");
            cerrar.setOnAction(evento -> pedirConclusionYCerrar(incidente));
            fila.getChildren().add(cerrar);
        }
        fila.setAlignment(Pos.CENTER_LEFT);

        VBox tarjeta = new VBox(fila);
        tarjeta.getStyleClass().add("panel-hundido");
        tarjeta.setPadding(new Insets(14, 16, 14, 16));
        return tarjeta;
    }

    private void pedirConclusionYCerrar(Incidente incidente) {
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle("Cerrar incidente");
        dialogo.setHeaderText("Cerrar el incidente #" + incidente.id());
        dialogo.setContentText("Conclusion:");
        dialogo.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/sica.css").toExternalForm());

        dialogo.showAndWait().ifPresent(conclusion -> {
            try {
                ComandoCerrarIncidente comando =
                        new ComandoCerrarIncidente(incidente.id(), conclusion);
                enSegundoPlano(
                        () -> contexto.cerrarIncidente().ejecutar(comando),
                        resultado -> {
                            mostrar(mensajeIncidente, resultado.mensaje(), "aviso-exito");
                            refrescarIncidentes();
                        },
                        mensajeIncidente);
            } catch (IllegalArgumentException e) {
                mostrar(mensajeIncidente, e.getMessage(), "aviso-error");
            }
        });
    }

    private void abrirFormularioIncidente() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Reportar incidente");
        dialogo.setHeaderText("Registrar un incidente de seguridad");
        dialogo.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/sica.css").toExternalForm());

        ComboBox<TipoIncidente> tipo = new ComboBox<>();
        tipo.getItems().setAll(TipoIncidente.values());
        tipo.setValue(TipoIncidente.OTRO);

        ComboBox<GravedadIncidente> gravedad = new ComboBox<>();
        gravedad.getItems().setAll(GravedadIncidente.values());
        gravedad.setValue(GravedadIncidente.MEDIA);

        TextField documento = new TextField();
        documento.setPromptText("documento (opcional)");

        TextArea descripcion = new TextArea();
        descripcion.setPromptText("Que ocurrio, con el mayor detalle posible.");
        descripcion.setPrefRowCount(5);
        descripcion.setWrapText(true);

        VBox formulario = new VBox(10,
                new VBox(4, Componentes.rotulo("Tipo"), tipo),
                new VBox(4, Componentes.rotulo("Gravedad"), gravedad),
                new VBox(4, Componentes.rotulo("Documento de la persona"), documento),
                new VBox(4, Componentes.rotulo("Descripcion"), descripcion));
        formulario.setPadding(new Insets(10));

        dialogo.getDialogPane().setContent(formulario);
        dialogo.getDialogPane().getButtonTypes()
               .addAll(ButtonType.OK, ButtonType.CANCEL);

        dialogo.showAndWait()
               .filter(boton -> boton == ButtonType.OK)
               .ifPresent(boton -> registrarIncidente(
                       tipo.getValue(), gravedad.getValue(),
                       documento.getText(), descripcion.getText()));
    }

    private void registrarIncidente(TipoIncidente tipo, GravedadIncidente gravedad,
                                    String documento, String descripcion) {
        ComandoRegistrarIncidente comando;
        try {
            comando = new ComandoRegistrarIncidente(
                    resolverPersona(documento), null, tipo, gravedad, descripcion);

        } catch (IllegalArgumentException e) {
            mostrar(mensajeIncidente, e.getMessage(), "aviso-error");
            return;
        }

        enSegundoPlano(
                () -> contexto.registrarIncidente().ejecutar(comando),
                resultado -> {
                    mostrar(mensajeIncidente, resultado.mensaje(),
                            resultado.sugiereBloqueo() ? "aviso-advertencia" : "aviso-exito");
                    refrescarIncidentes();
                },
                mensajeIncidente);
    }

    /**
     * Traduce un documento a personaId. La pantalla no debe pedirle al usuario
     * un identificador interno de la base de datos: el guarda conoce cedulas,
     * no claves primarias.
     *
     * Si el documento no corresponde a nadie registrado, devuelve null y el
     * incidente queda sin persona asociada, que es correcto: un intento de
     * ingreso con documento ajeno es justamente un caso donde no hay una
     * persona valida a la que enlazarlo.
     */
    private Long resolverPersona(String documento) {
        if (documento == null || documento.isBlank()) {
            return null;
        }
        // Se busca en el directorio de personas y NO con el caso de uso de
        // porteria. Un supervisor no tiene el permiso 'consultar_porteria' y no
        // deberia tenerlo: su trabajo es la supervision, no operar la entrada.
        // Usar aqui el caso de uso del guarda habria forzado a ampliarle
        // permisos que no le corresponden solo para que funcione una pantalla.
        return contexto.personas().listar(documento.trim()).stream()
                .filter(persona -> persona.documentoIdentidad().equals(documento.trim()))
                .map(Persona::id)
                .findFirst()
                .orElse(null);
    }

    // ==================================================================
    //  PESTAÑA 3 — AUDITORIA
    // ==================================================================

    private VBox pestanaBitacora() {
        Button verificar = new Button("Verificar integridad");
        verificar.getStyleClass().add("boton-primario");
        verificar.setOnAction(evento -> verificarIntegridad());

        Button refrescar = new Button("Refrescar");
        refrescar.getStyleClass().add("boton-secundario");
        refrescar.setOnAction(evento -> refrescarBitacora());

        franjaIntegridad.getStyleClass().add("aviso");
        franjaIntegridad.setWrapText(true);
        franjaIntegridad.setMaxWidth(Double.MAX_VALUE);
        ocultar(franjaIntegridad);

        ScrollPane desplazable = new ScrollPane(listaBitacora);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("panel-hundido");
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        VBox contenido = new VBox(14,
                Componentes.con("Cada registro incluye el hash del anterior. Alterar o borrar "
                              + "una fila rompe la cadena, y el sistema informa en cual.",
                                "apagado"),
                new HBox(12, verificar, refrescar),
                franjaIntegridad,
                desplazable);
        contenido.getStyleClass().add("panel");
        contenido.setPadding(new Insets(24, 28, 24, 28));
        return contenido;
    }

    private void refrescarBitacora() {
        enSegundoPlano(() -> contexto.bitacora().consultarUltimos(200),
                       this::pintarBitacora, franjaIntegridad);
    }

    private void pintarBitacora(List<LineaBitacora> lineas) {
        listaBitacora.getChildren().clear();
        if (lineas.isEmpty()) {
            listaBitacora.getChildren().add(
                    Componentes.con("La bitacora esta vacia.", "apagado"));
            return;
        }
        lineas.stream().map(this::filaBitacora)
              .forEach(listaBitacora.getChildren()::add);
    }

    private HBox filaBitacora(LineaBitacora linea) {
        boolean fallo = "FALLO".equalsIgnoreCase(linea.resultado());

        HBox fila = new HBox(12,
                anchoFijo(Componentes.con("#" + linea.id(), "mono", "microtexto"), 56),
                anchoFijo(Componentes.con(
                        linea.fechaHora() == null ? "" : linea.fechaHora().format(FECHA_HORA),
                        "mono", "microtexto"), 150),
                anchoFijo(Componentes.con(linea.usuario(), "microtexto"), 190),
                anchoFijo(Componentes.con(linea.accion(), "mono",
                        fallo ? "texto-rojo" : "texto-verde"), 190),
                Componentes.con(linea.detalle() == null ? "" : linea.detalle(), "microtexto"));
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setPadding(new Insets(3, 6, 3, 6));
        return fila;
    }

    private void verificarIntegridad() {
        enSegundoPlano(() -> contexto.bitacora().verificarIntegridad(),
                (ResultadoIntegridad resultado) ->
                        mostrar(franjaIntegridad, resultado.mensaje(),
                                resultado.intacta() ? "aviso-exito" : "aviso-error"),
                franjaIntegridad);
    }

    // ==================================================================
    //  UTILIDADES
    // ==================================================================

    /**
     * Oculta un control si el usuario no tiene el permiso.
     *
     * setManaged(false) ademas de setVisible(false) para que el hueco tampoco
     * ocupe espacio: un boton invisible que deja un vacio en la fila se ve como
     * un error de maquetacion.
     */
    private void soloConPermiso(javafx.scene.Node control, String permiso) {
        boolean puede = supervisor.tienePermiso(permiso);
        control.setVisible(puede);
        control.setManaged(puede);
    }

    private Region anchoFijo(Label etiqueta, double ancho) {
        etiqueta.setMinWidth(ancho);
        etiqueta.setMaxWidth(ancho);
        return etiqueta;
    }

    private void mostrar(Label destino, String texto, String clase) {
        destino.setText(texto);
        destino.getStyleClass().removeAll("aviso-exito", "aviso-error", "aviso-advertencia");
        destino.getStyleClass().add(clase);
        destino.setVisible(true);
        destino.setManaged(true);
    }

    private void ocultar(Label etiqueta) {
        etiqueta.setVisible(false);
        etiqueta.setManaged(false);
    }

    /**
     * Todo trabajo de base de datos va a un hilo aparte. Un reporte de un mes
     * puede tardar; hacerlo en el hilo de JavaFX congelaria la ventana entera.
     */
    private <T> void enSegundoPlano(Supplier<T> trabajo, Consumer<T> alTerminar, Label destinoError) {
        Task<T> tarea = new Task<>() {
            @Override protected T call() { return trabajo.get(); }
        };
        tarea.setOnSucceeded(evento ->
                Platform.runLater(() -> alTerminar.accept(tarea.getValue())));
        tarea.setOnFailed(evento -> Platform.runLater(() -> {
            Throwable causa = tarea.getException();
            mostrar(destinoError, causa instanceof ExcepcionDominio
                    ? causa.getMessage()
                    : "Ocurrio un error: " + causa.getMessage(), "aviso-error");
        }));
        Thread hilo = new Thread(tarea, "sica-supervisor");
        hilo.setDaemon(true);
        hilo.start();
    }
}
