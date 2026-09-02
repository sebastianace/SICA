package com.zonaacme.sica.shared.infraestructura;

import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;

import java.io.InputStream;
import java.util.Properties;

/** Lee config.properties del classpath. Ese archivo esta en .gitignore. */
public final class ConfiguracionApp {

    private static final Properties PROPIEDADES = cargar();

    private ConfiguracionApp() { }

    private static Properties cargar() {
        Properties props = new Properties();
        try (InputStream entrada = ConfiguracionApp.class
                .getResourceAsStream("/config.properties")) {

            if (entrada == null) {
                throw new ExcepcionTecnica(
                        "No se encontro config.properties en src/main/resources. "
                      + "Copia config.properties.example, renombralo y pon tus credenciales.", null);
            }
            props.load(entrada);
            return props;

        } catch (java.io.IOException e) {
            throw new ExcepcionTecnica("No se pudo leer config.properties", e);
        }
    }

    public static String urlBaseDatos()     { return obligatoria("db.url"); }
    public static String usuarioBaseDatos() { return obligatoria("db.usuario"); }
    public static String claveBaseDatos()   { return PROPIEDADES.getProperty("db.password", ""); }
    public static String terminal()         { return PROPIEDADES.getProperty("app.terminal", "DESCONOCIDA"); }

    private static String obligatoria(String clave) {
        String valor = PROPIEDADES.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new ExcepcionTecnica("Falta la propiedad '" + clave + "' en config.properties", null);
        }
        return valor;
    }
}
