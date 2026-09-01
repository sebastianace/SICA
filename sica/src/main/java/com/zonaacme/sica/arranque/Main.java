package com.zonaacme.sica.arranque;

import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;
import com.zonaacme.sica.shared.infraestructura.ConexionBD;
import com.zonaacme.sica.ui.VentanaLogin;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Punto de entrada.
 *
 * Antes de mostrar nada, verifica la conexion a MySQL. Fallar temprano con un
 * mensaje claro es mucho mejor que abrir la ventana y que el primer login
 * reviente con una traza incomprensible.
 */
public final class Main extends Application {

    @Override
    public void start(Stage escenarioPrincipal) {
        try {
            ConexionBD.verificarConexion();
        } catch (ExcepcionTecnica fallo) {
            mostrarErrorDeArranque(fallo);
            return;
        }
        new VentanaLogin(new ContextoAplicacion()).mostrarEn(escenarioPrincipal);
    }

    private void mostrarErrorDeArranque(ExcepcionTecnica fallo) {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle("SICA no pudo arrancar");
        alerta.setHeaderText("No hay conexion con la base de datos");
        alerta.setContentText("""
                %s

                Revisa, en este orden:
                  1. Que el servicio de MySQL este corriendo.
                  2. Que exista la base de datos 'sica' (ejecuta schema.sql y data.sql).
                  3. Que src/main/resources/config.properties tenga la URL y las credenciales correctas.
                """.formatted(fallo.getMessage()));
        alerta.getDialogPane().setPrefWidth(560);
        alerta.showAndWait();
    }

    public static void main(String[] argumentos) {
        launch(argumentos);
    }
}
