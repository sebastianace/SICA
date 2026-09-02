import com.zonaacme.sica.arranque.ContextoAplicacion;
import com.zonaacme.sica.usuarios.aplicacion.ComandoLogin;
import com.zonaacme.sica.reportes.aplicacion.*;
import com.zonaacme.sica.incidentes.aplicacion.*;
import com.zonaacme.sica.incidentes.dominio.*;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;
import java.time.LocalDate;

public class PruebaReportes {
    static int fallas = 0;
    static void check(String e, boolean ok, String d) {
        System.out.printf("  %-50s %-6s %s%n", e, ok?"OK":"FALLA", d);
        if(!ok) fallas++;
    }
    public static void main(String[] a) throws Exception {
        ContextoAplicacion ctx = new ContextoAplicacion();

        System.out.println("\n--- REPORTES (supervisor) ---");
        UsuarioAutenticado sup = ctx.iniciarSesion()
                .ejecutar(new ComandoLogin("palvarez@zonaacme.co","Super123*".toCharArray()));
        ctx.sesion().iniciar(sup);
        check("Supervisor puede generar reportes", sup.tienePermiso("generar_reporte"), "");

        ReporteVisitas r = ctx.generarReporteVisitas()
                .ejecutar(ComandoReporteVisitas.deTodoElComplejo(
                        LocalDate.now().minusDays(10), LocalDate.now()));

        check("El reporte trae detalle", !r.estaVacio(), r.totalVisitas() + " visitas");
        check("Cuenta las que estan dentro ahora", r.totalDentroAhora() > 0,
              r.totalDentroAhora() + " dentro");
        check("groupingBy por empresa", !r.visitasPorEmpresa().isEmpty(),
              r.visitasPorEmpresa().toString());
        check("groupingBy por tipo de visita", !r.visitasPorTipo().isEmpty(),
              r.visitasPorTipo().toString());
        check("groupingBy por estado", !r.visitasPorEstado().isEmpty(),
              r.visitasPorEstado().toString());
        check("Ingresos por hora ordenados por hora", !r.ingresosPorHora().isEmpty(),
              r.ingresosPorHora().toString());
        check("averagingLong: estancia promedio", !r.promedioEstanciaPorEmpresa().isEmpty(),
              r.promedioEstanciaPorEmpresa().toString());
        check("Top estancias mas largas", !r.estanciasMasLargas().isEmpty(),
              r.estanciasMasLargas().size() + " listadas");
        check("Detecta salidas olvidadas", r.totalCerradasPorSistema() > 0,
              String.format("%.1f%% del total con ingreso", r.porcentajeSalidasOlvidadas()));

        // El orden descendente debe respetarse pese a que groupingBy da HashMap
        var valores = r.visitasPorEmpresa().values().stream().toList();
        boolean descendente = true;
        for (int i = 1; i < valores.size(); i++)
            if (valores.get(i) > valores.get(i-1)) descendente = false;
        check("El orden descendente se conserva", descendente, valores.toString());

        String csv = GenerarReporteVisitasService.aCsv(r);
        check("CSV con encabezado + filas",
              csv.split("\n").length == r.totalVisitas() + 1,
              csv.split("\n").length + " lineas");
        check("CSV usa punto y coma", csv.split("\n")[0].contains(";"), "");

        System.out.println("\n--- INCIDENTES ---");
        var incidentesPrevios = ctx.incidentes().listarTodos();
        check("Lista los incidentes sembrados", incidentesPrevios.size() >= 2,
              incidentesPrevios.size() + " incidentes");
        check("Los abiertos van primero", incidentesPrevios.get(0).estaAbierto(),
              "primero: " + incidentesPrevios.get(0).gravedad());

        ResultadoIncidente nuevo = ctx.registrarIncidente().ejecutar(
                new ComandoRegistrarIncidente(1L, null, TipoIncidente.OBJETO_PROHIBIDO,
                        GravedadIncidente.CRITICA, "Intento de ingreso con objeto prohibido."));
        check("Incidente registrado", nuevo.incidenteId() > 0, "#" + nuevo.incidenteId());
        check("Gravedad critica sugiere bloqueo", nuevo.sugiereBloqueo(), nuevo.mensaje());

        ResultadoIncidente leve = ctx.registrarIncidente().ejecutar(
                new ComandoRegistrarIncidente(1L, null, TipoIncidente.OTRO,
                        GravedadIncidente.BAJA, "Observacion menor."));
        check("Gravedad baja NO sugiere bloqueo", !leve.sugiereBloqueo(), "");

        ctx.cerrarIncidente().ejecutar(
                new ComandoCerrarIncidente(nuevo.incidenteId(), "Objeto retirado y devuelto."));
        boolean noReabre = false; String msg="";
        try { ctx.cerrarIncidente().ejecutar(
                new ComandoCerrarIncidente(nuevo.incidenteId(), "otra vez")); }
        catch (Exception ex) { noReabre = true; msg = ex.getMessage(); }
        check("Un incidente cerrado no se vuelve a cerrar", noReabre, msg);

        System.out.println("\n--- RBAC Y AUDITORIA ---");
        UsuarioAutenticado guarda = ctx.iniciarSesion()
                .ejecutar(new ComandoLogin("hrincon@zonaacme.co","Guarda123*".toCharArray()));
        ctx.sesion().iniciar(guarda);
        boolean frenado=false;
        try { ctx.generarReporteVisitas().ejecutar(ComandoReporteVisitas.ultimosDias(7)); }
        catch (com.zonaacme.sica.shared.dominio.ExcepcionPermisoDenegado e){ frenado=true; }
        check("Un guarda NO puede generar reportes", frenado, "");
        check("Pero SI puede reportar incidentes",
              guarda.tienePermiso("registrar_incidente"), "");

        var integridad = ctx.bitacora().verificarIntegridad();
        check("Bitacora integra tras todo", integridad.intacta(), integridad.mensaje());

        System.out.println(fallas==0 ? "\n>>> REPORTES E INCIDENTES OK\n" : "\n>>> "+fallas+" FALLAS\n");
        System.exit(fallas==0?0:1);
    }
}
