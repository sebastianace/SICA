package com.zonaacme.sica.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Piezas visuales reutilizables. Evita repetir el mismo armado en cada pantalla. */
public final class Componentes {

    private Componentes() { }

    public static Label rotulo(String texto) {
        Label etiqueta = new Label(texto.toUpperCase());
        etiqueta.getStyleClass().add("rotulo");
        return etiqueta;
    }

    public static Label con(String texto, String... clases) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().addAll(clases);
        return etiqueta;
    }

    public static Region espaciadorHorizontal() {
        Region region = new Region();
        javafx.scene.layout.HBox.setHgrow(region, javafx.scene.layout.Priority.ALWAYS);
        return region;
    }

    public static Region espaciadorVertical() {
        Region region = new Region();
        javafx.scene.layout.VBox.setVgrow(region, javafx.scene.layout.Priority.ALWAYS);
        return region;
    }

    /**
     * Separa la palabra en caracteres con espacios.
     *
     * JavaFX no tiene -fx-letter-spacing en CSS. Como la franja de veredicto
     * necesita esa apertura para leerse a distancia, se resuelve en el texto.
     */
    public static String espaciada(String palabra) {
        return String.join(" ", palabra.split(""));
    }

    /**
     * Foto de la persona con carga en segundo plano.
     *
     * El ultimo parametro de Image activa la descarga asincrona: si la URL
     * responde lento, la interfaz no se congela. Si falla, queda el marco vacio
     * con las iniciales, nunca una excepcion en pantalla.
     */
    public static StackPane foto(String url, String nombreCompleto, double lado) {
        StackPane marco = new StackPane();
        marco.getStyleClass().add("marco-foto");
        marco.setMinSize(lado, lado);
        marco.setMaxSize(lado, lado);
        marco.setAlignment(Pos.CENTER);

        Label iniciales = new Label(inicialesDe(nombreCompleto));
        iniciales.setStyle("-fx-font-size: " + (lado / 3.2) + "px; -fx-font-weight: 700;");
        iniciales.getStyleClass().add("apagado");
        marco.getChildren().add(iniciales);

        if (url != null && !url.isBlank()) {
            Image imagen = new Image(url, lado, lado, true, true, true);
            ImageView vista = new ImageView(imagen);
            vista.setFitWidth(lado);
            vista.setFitHeight(lado);
            vista.setPreserveRatio(true);

            imagen.errorProperty().addListener((observable, antes, hayError) -> {
                if (Boolean.TRUE.equals(hayError)) {
                    marco.getChildren().remove(vista);
                }
            });
            marco.getChildren().add(vista);
        }
        return marco;
    }

    private static String inicialesDe(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "?";

        String[] partes = nombreCompleto.trim().split("\\s+");
        StringBuilder iniciales = new StringBuilder();
        iniciales.append(Character.toUpperCase(partes[0].charAt(0)));

        if (partes.length > 1) {
            iniciales.append(Character.toUpperCase(partes[partes.length - 1].charAt(0)));
        }
        return iniciales.toString();
    }
}
