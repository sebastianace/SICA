package com.zonaacme.sica.shared.infraestructura;

import com.zonaacme.sica.acceso.dominio.EstadoVisita;
import com.zonaacme.sica.shared.dominio.ExcepcionTecnica;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traduce entre los estados del DOMINIO y las filas de las tablas de consulta
 * de la base de datos.
 *
 * Existe por una diferencia deliberada entre las dos representaciones:
 *
 *   En Java, EstadoVisita es un enum CON COMPORTAMIENTO. Sabe a que estados
 *   puede transicionar y rechaza los cambios ilegales. Es el patron State.
 *
 *   En la base de datos, el modelo entregado normaliza los estados en las
 *   tablas visita_estados y persona_estados_acceso, con nombres legibles.
 *
 * Ninguna de las dos representaciones es incorrecta: sirven a propositos
 * distintos. La base de datos quiere integridad referencial y nombres que un
 * humano pueda leer en una consulta; el dominio quiere reglas que no se puedan
 * violar. Esta clase es la costura entre ambas.
 *
 * Que esta traduccion viva aqui, en infraestructura, es lo que permite que el
 * dominio ignore por completo que existen tablas de estados. Si manana se
 * volviera a un ENUM, o a un microservicio, solo cambiaria esta clase.
 *
 * Los identificadores se leen una vez y se guardan en memoria: son datos de
 * catalogo que no cambian mientras la aplicacion corre, y consultarlos en cada
 * operacion de porteria seria un viaje a la base de datos por cada tecla.
 */
public final class CatalogoEstados {

    // ---- Nombres tal como estan escritos en la base de datos ----

    private static final Map<EstadoVisita, String> NOMBRE_EN_BD = Map.of(
            EstadoVisita.APROBADA,                    "Aprobado",
            EstadoVisita.PENDIENTE_APROBACION,        "Pendiente de Aprobacion",
            EstadoVisita.PENDIENTE_APROBACION_OLVIDO, "Pendiente de Aprobacion Olvido",
            EstadoVisita.RECHAZADA,                   "Rechazado",
            EstadoVisita.DENTRO,                      "Dentro",
            EstadoVisita.FINALIZADA,                  "Finalizado",
            EstadoVisita.CERRADA_POR_SISTEMA,         "Cerrado por Sistema",
            EstadoVisita.EXPIRADA,                    "Expirado");

    public static final String ACCESO_PERMITIDO = "Activo";
    public static final String ACCESO_BLOQUEADO = "Con Prohibicion de Ingreso";

    // ---- Caches de catalogo ----

    private static final Map<String, Integer> ID_VISITA_ESTADO  = new ConcurrentHashMap<>();
    private static final Map<Integer, String> NOMBRE_VISITA_POR_ID = new ConcurrentHashMap<>();
    private static final Map<String, Integer> ID_PERSONA_ESTADO = new ConcurrentHashMap<>();

    private static volatile boolean cargado = false;

    private CatalogoEstados() { }

    /**
     * Garantiza que los catalogos esten cargados antes de usarlos.
     *
     * La carga tambien se dispara al verificar la conexion en el arranque, que
     * es lo deseable porque asi un catalogo incompleto se detecta antes de
     * mostrar la primera ventana. Pero no se depende de ese orden: cualquier
     * punto de entrada que necesite traducir un estado abre su propia conexion
     * si hace falta. Depender del orden de arranque es fragil.
     */
    private static void asegurarCargado() {
        if (cargado) {
            return;
        }
        try (Connection conexion = ConexionBD.abrir()) {
            cargar(conexion);
        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudieron cargar los catalogos de estados.", e);
        }
    }

    /**
     * Carga los catalogos una sola vez. Se invoca desde ConexionBD al verificar
     * la conexion, antes de que la aplicacion muestre nada.
     */
    public static synchronized void cargar(Connection conexion) {
        if (cargado) {
            return;
        }
        try {
            leerCatalogo(conexion, "visita_estados", ID_VISITA_ESTADO, NOMBRE_VISITA_POR_ID);
            leerCatalogo(conexion, "persona_estados_acceso", ID_PERSONA_ESTADO, new HashMap<>());

            // Si falta un estado en la base de datos, es mejor saberlo al
            // arrancar que descubrirlo cuando un guarda registre un ingreso.
            for (EstadoVisita estado : EstadoVisita.values()) {
                String nombre = NOMBRE_EN_BD.get(estado);
                if (!ID_VISITA_ESTADO.containsKey(nombre)) {
                    throw new ExcepcionTecnica(
                            "Falta el estado '" + nombre + "' en la tabla visita_estados. "
                          + "Ejecuta data.sql completo.");
                }
            }
            cargado = true;

        } catch (SQLException e) {
            throw new ExcepcionTecnica("No se pudieron cargar los catalogos de estados.", e);
        }
    }

    private static void leerCatalogo(Connection conexion, String tabla,
                                     Map<String, Integer> porNombre,
                                     Map<Integer, String> porId) throws SQLException {

        String sql = "SELECT id, nombre_estado FROM " + tabla;
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet fila = sentencia.executeQuery()) {

            while (fila.next()) {
                int    id     = fila.getInt("id");
                String nombre = fila.getString("nombre_estado");
                porNombre.put(nombre, id);
                porId.put(id, nombre);
            }
        }
    }

    // ---- Estados de visita ----

    public static int idDe(EstadoVisita estado) {
        asegurarCargado();
        Integer id = ID_VISITA_ESTADO.get(NOMBRE_EN_BD.get(estado));
        if (id == null) {
            throw new ExcepcionTecnica("Estado de visita sin equivalente en la base de datos: " + estado);
        }
        return id;
    }

    public static EstadoVisita estadoDeId(int id) {
        asegurarCargado();
        String nombre = NOMBRE_VISITA_POR_ID.get(id);
        return NOMBRE_EN_BD.entrySet().stream()
                .filter(entrada -> entrada.getValue().equals(nombre))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new ExcepcionTecnica(
                        "La base de datos tiene un estado de visita desconocido: id=" + id));
    }

    /** Nombre en la base de datos, para consultas que filtran por texto. */
    public static String nombreDe(EstadoVisita estado) {
        return NOMBRE_EN_BD.get(estado);
    }

    // ---- Estados de acceso de una persona ----

    public static int idAccesoPermitido() {
        return idAcceso(ACCESO_PERMITIDO);
    }

    public static int idAccesoBloqueado() {
        return idAcceso(ACCESO_BLOQUEADO);
    }

    private static int idAcceso(String nombre) {
        asegurarCargado();
        Integer id = ID_PERSONA_ESTADO.get(nombre);
        if (id == null) {
            throw new ExcepcionTecnica("Falta el estado de acceso '" + nombre
                                     + "' en persona_estados_acceso.");
        }
        return id;
    }

    /** True si el nombre almacenado corresponde a una prohibicion de ingreso. */
    public static boolean estaBloqueado(String nombreEstadoAcceso) {
        return ACCESO_BLOQUEADO.equalsIgnoreCase(nombreEstadoAcceso);
    }
}
