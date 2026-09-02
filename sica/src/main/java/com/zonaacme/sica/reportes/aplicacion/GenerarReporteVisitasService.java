package com.zonaacme.sica.reportes.aplicacion;

import com.zonaacme.sica.shared.aplicacion.CasoDeUso;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Genera el reporte de visitas de un rango de fechas.
 *
 * Toda la agregacion se hace con Stream API sobre las filas de detalle que
 * devuelve el puerto. La eleccion no es estetica: sobre el MISMO conjunto de
 * filas se construyen seis cortes distintos. Resolverlos con GROUP BY seria
 * seis consultas sobre los mismos datos.
 *
 * Un detalle que se repite abajo y conviene entender: los agrupamientos usan
 * LinkedHashMap explicitamente. Collectors.groupingBy entrega un HashMap, que
 * NO garantiza orden, asi que ordenar antes de agrupar no sirve de nada: el
 * mapa reordena las claves por su cuenta. Hay que ordenar y volcar el resultado
 * en un mapa que respete el orden de insercion.
 */
public final class GenerarReporteVisitasService
        implements CasoDeUso<ComandoReporteVisitas, ReporteVisitas> {

    /** Cuantas estancias largas se listan para revision manual. */
    private static final int TOPE_ESTANCIAS_LARGAS = 10;

    private final ConsultaReportes consulta;

    public GenerarReporteVisitasService(ConsultaReportes consulta) {
        this.consulta = consulta;
    }

    @Override
    public ReporteVisitas ejecutar(ComandoReporteVisitas comando) {

        // SQL hace lo que hace bien: filtrar por fecha y empresa usando indices.
        List<FilaVisita> filas = consulta.visitasEntre(
                comando.desde(), comando.hasta(), comando.empresaId());

        return new ReporteVisitas(
                filas,
                filas.size(),
                (int) filas.stream().filter(FilaVisita::tuvoIngreso).count(),
                (int) filas.stream().filter(FilaVisita::estaDentro).count(),
                (int) filas.stream().filter(FilaVisita::fueCerradaPorSistema).count(),
                conteoDescendentePor(filas, FilaVisita::empresa),
                conteoDescendentePor(filas, FilaVisita::tipoVisita),
                conteoDescendentePor(filas, FilaVisita::estado),
                ingresosPorHora(filas),
                promedioEstanciaPorEmpresa(filas),
                estanciasMasLargas(filas));
    }

    /**
     * Cuenta cuantas filas hay por cada valor de un atributo y las devuelve de
     * mayor a menor.
     *
     * Es generico a proposito: el corte por empresa, por tipo y por estado son
     * la misma operacion sobre distinto campo. La funcion que extrae el campo
     * se pasa como parametro, que es exactamente para lo que sirven las
     * lambdas: parametrizar comportamiento, no solo datos.
     */
    private <T> Map<T, Long> conteoDescendentePor(List<FilaVisita> filas,
                                                  Function<FilaVisita, T> atributo) {
        return filas.stream()
                .filter(fila -> atributo.apply(fila) != null)
                .collect(Collectors.groupingBy(atributo, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<T, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * Ingresos por hora del dia.
     *
     * Se ordena por hora, no por cantidad: aqui interesa la forma de la curva a
     * lo largo del dia. Ordenarla por volumen destruiria justamente el patron
     * que se quiere ver.
     */
    private Map<Integer, Long> ingresosPorHora(List<FilaVisita> filas) {
        return filas.stream()
                .map(FilaVisita::horaDeIngreso)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.groupingBy(
                        hora -> hora,
                        java.util.TreeMap::new,
                        Collectors.counting()));
    }

    /**
     * Duracion promedio de estancia por empresa, solo sobre visitas cerradas.
     *
     * Las visitas abiertas se excluyen a proposito: incluirlas haria que el
     * promedio cambiara cada minuto y no seria comparable entre corridas.
     */
    private Map<String, Double> promedioEstanciaPorEmpresa(List<FilaVisita> filas) {
        return filas.stream()
                .filter(fila -> fila.minutosDeEstancia().isPresent())
                .collect(Collectors.groupingBy(
                        FilaVisita::empresa,
                        java.util.TreeMap::new,
                        Collectors.averagingLong(fila -> fila.minutosDeEstancia().orElse(0L))));
    }

    /** Las estancias cerradas mas largas: candidatas a revision manual. */
    private List<FilaVisita> estanciasMasLargas(List<FilaVisita> filas) {
        return filas.stream()
                .filter(fila -> fila.minutosDeEstancia().isPresent())
                .sorted(Comparator.comparingLong(
                        (FilaVisita fila) -> fila.minutosDeEstancia().orElse(0L)).reversed())
                .limit(TOPE_ESTANCIAS_LARGAS)
                .toList();
    }

    /**
     * Exporta un reporte a CSV.
     *
     * Se construye con Stream.concat para que el encabezado y las filas sean un
     * solo flujo: asi no hay un StringBuilder mutable al que haya que acordarse
     * de agregarle el encabezado antes del bucle.
     */
    public static String aCsv(ReporteVisitas reporte) {
        String encabezado = String.join(";",
                "visita_id", "documento", "nombre", "tipo_persona", "empresa",
                "tipo_visita", "estado", "fecha_entrada", "fecha_salida",
                "minutos_estancia", "registrada_por");

        Stream<String> lineas = reporte.detalle().stream().map(fila -> String.join(";",
                String.valueOf(fila.visitaId()),
                limpiar(fila.documento()),
                limpiar(fila.nombrePersona()),
                limpiar(fila.tipoPersona()),
                limpiar(fila.empresa()),
                limpiar(fila.tipoVisita()),
                limpiar(fila.estado()),
                textoDe(fila.fechaEntrada()),
                textoDe(fila.fechaSalida()),
                fila.minutosDeEstancia().map(String::valueOf).orElse(""),
                limpiar(fila.registradaPor())));

        return Stream.concat(Stream.of(encabezado), lineas)
                     .collect(Collectors.joining("\n"));
    }

    /**
     * El separador es punto y coma porque Excel en configuracion regional de
     * Colombia usa la coma como separador decimal: con comas, un archivo
     * exportado se abre con las columnas corridas.
     *
     * Si un nombre trae punto y coma o un salto de linea, romperia la fila; se
     * reemplazan.
     */
    private static String limpiar(String valor) {
        return valor == null ? "" : valor.replace(';', ',').replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String textoDe(java.time.LocalDateTime fecha) {
        return fecha == null ? "" : fecha.toString().replace('T', ' ');
    }
}
