import com.zonaacme.sica.arranque.ContextoAplicacion;
import com.zonaacme.sica.usuarios.aplicacion.*;
import com.zonaacme.sica.acceso.aplicacion.*;
import com.zonaacme.sica.acceso.dominio.*;
import com.zonaacme.sica.shared.seguridad.*;

public class PruebaFlujos {
    static int fallas = 0;
    static void check(String etiqueta, boolean ok, String detalle) {
        System.out.printf("  %-48s %-6s %s%n", etiqueta, ok ? "OK" : "FALLA", detalle);
        if (!ok) fallas++;
    }
    public static void main(String[] args) throws Exception {
        ContextoAplicacion ctx = new ContextoAplicacion();

        System.out.println("\n--- AUTENTICACION Y RBAC ---");
        UsuarioAutenticado guarda = ctx.iniciarSesion()
                .ejecutar(new ComandoLogin("hrincon@zonaacme.co", "Guarda123*".toCharArray()));
        check("Login guarda1", guarda != null, guarda.nombreCompleto());
        check("Permisos cargados desde la BD", !guarda.permisos().isEmpty(),
              guarda.permisos().size() + " permisos");
        check("Guarda puede registrar visitas",
              guarda.tienePermiso("registrar_visita"), "");
        check("Guarda NO puede crear usuarios",
              !guarda.tienePermiso("crear_usuario"), "denegado correctamente");

        boolean rechazo = false;
        try { ctx.iniciarSesion().ejecutar(new ComandoLogin("hrincon@zonaacme.co", "malaClave".toCharArray())); }
        catch (Exception e) { rechazo = true; }
        check("Clave incorrecta rechazada", rechazo, "");

        ctx.sesion().iniciar(guarda);

        // RBAC de verdad: el decorador debe FRENAR la ejecucion, no solo
        // responder false a una pregunta sobre permisos.
        UsuarioAutenticado funcionario = ctx.iniciarSesion()
                .ejecutar(new ComandoLogin("mrodriguez@nexoanalytics.co", "Funciona123*".toCharArray()));
        ctx.sesion().iniciar(funcionario);
        boolean frenado = false;
        try {
            ctx.registrarIngreso().ejecutar(
                    ComandoRegistrarIngreso.rutinaDeTrabajador("CC", "1015234567", 1L));
        } catch (com.zonaacme.sica.shared.dominio.ExcepcionPermisoDenegado e) {
            frenado = true;
        }
        check("RBAC frena al funcionario en registrar ingreso", frenado,
              "SeguridadDecorator lanzo ExcepcionPermisoDenegado");
        ctx.sesion().iniciar(guarda);

        System.out.println("\n--- CONSULTA DE PORTERIA (los 5 veredictos) ---");
        FichaPorteria f1 = ctx.consultarPorteria().ejecutar(new ComandoConsultarPorteria("CC","1020304050"));
        check("Camila (flujo 1) autorizada", f1.veredicto()==Veredicto.AUTORIZADO, f1.veredicto().palabra());
        FichaPorteria fb = ctx.consultarPorteria().ejecutar(new ComandoConsultarPorteria("CC","1011223344"));
        check("Oscar bloqueado se detiene", fb.veredicto()==Veredicto.BLOQUEADO, fb.veredicto().palabra());
        FichaPorteria fn = ctx.consultarPorteria().ejecutar(new ComandoConsultarPorteria("CC","9999999999"));
        check("Documento desconocido", fn.veredicto()==Veredicto.NO_REGISTRADO, fn.veredicto().palabra());

        System.out.println("\n--- FLUJO 4: SALIDA OLVIDADA (transaccional) ---");
        int antes = ctx.visitas().ocupacionActual().size();
        check("Tablero de ocupacion responde", antes > 0, antes + " personas dentro");

        FichaPorteria f4 = ctx.consultarPorteria().ejecutar(new ComandoConsultarPorteria("CC","1032998877"));
        check("Juliana detectada con visita abierta", f4.visitaAbiertaId()!=null,
              "visita #" + f4.visitaAbiertaId() + " desde " + f4.ingresoDeVisitaAbierta());
        check("Hora de la visita abierta ya no es null", f4.ingresoDeVisitaAbierta()!=null, String.valueOf(f4.ingresoDeVisitaAbierta()));
        long empresaId = f4.empresaSugeridaId();

        ResultadoIngreso r4 = ctx.registrarIngreso()
                .ejecutar(ComandoRegistrarIngreso.rutinaDeTrabajador("CC","1032998877", empresaId));
        check("Se cerro la visita olvidada", r4.cerroVisitaOlvidada(),
              "cerro #" + r4.visitaCerradaId() + ", abrio #" + r4.visitaId());

        int despues = ctx.visitas().ocupacionActual().size();
        check("INVARIANTE: la ocupacion no se duplico", despues==antes,
              "antes=" + antes + " despues=" + despues);
        long abiertas = ctx.visitas().ocupacionActual().stream()
                .filter(o -> o.documento().contains("1032998877")).count();
        check("INVARIANTE: Juliana con UNA visita abierta", abiertas==1, "abiertas=" + abiertas);

        System.out.println("\n--- SALIDA NORMAL ---");
        ResultadoSalida rs = ctx.registrarSalida()
                .ejecutar(new ComandoRegistrarSalida("CC","1032998877"));
        check("Salida registrada", rs != null, rs.mensaje());
        check("Ya no aparece en el tablero",
              ctx.visitas().ocupacionActual().stream()
                 .noneMatch(o -> o.documento().contains("1032998877")), "");

        System.out.println("\n--- BITACORA DE AUDITORIA ---");
        var lineas = ctx.bitacora().consultarUltimos(50);
        check("Escrita desde la capa de servicio Java", !lineas.isEmpty(), lineas.size()+" registros");
        var integridad = ctx.bitacora().verificarIntegridad();
        check("Cadena de hash integra", integridad.intacta(), integridad.mensaje());

        System.out.println(fallas==0 ? "\n>>> TODOS LOS FLUJOS PASAN\n" : "\n>>> "+fallas+" FALLAS\n");
        System.exit(fallas==0 ? 0 : 1);
    }
}
