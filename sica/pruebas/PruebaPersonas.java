import com.zonaacme.sica.arranque.ContextoAplicacion;
import com.zonaacme.sica.usuarios.aplicacion.ComandoLogin;
import com.zonaacme.sica.personas.aplicacion.*;
import com.zonaacme.sica.personas.dominio.TipoPersona;
import com.zonaacme.sica.acceso.aplicacion.*;
import com.zonaacme.sica.acceso.dominio.*;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;

public class PruebaPersonas {
    static int fallas = 0;
    static void check(String e, boolean ok, String d) {
        System.out.printf("  %-52s %-6s %s%n", e, ok?"OK":"FALLA", d);
        if(!ok) fallas++;
    }
    public static void main(String[] a) throws Exception {
        ContextoAplicacion ctx = new ContextoAplicacion();
        UsuarioAutenticado sup = ctx.iniciarSesion()
                .ejecutar(new ComandoLogin("palvarez@zonaacme.co","Super123*".toCharArray()));
        ctx.sesion().iniciar(sup);

        System.out.println("\n--- DIRECTORIO (flujos 2 y 3 desde la UI) ---");
        var empresas = ctx.directorio().empresasActivas();
        check("Lista empresas activas", empresas.size() >= 5, empresas.size() + " empresas");
        var anfitriones = ctx.directorio().anfitrionesDe(1L);
        check("Anfitriones de Nexo Analytics", !anfitriones.isEmpty(),
              anfitriones.toString());
        check("Filtra por permiso, no por rol",
              anfitriones.stream().allMatch(x -> x.empresaId()==1L), "");

        System.out.println("\n--- CRUD DE PERSONAS ---");
        check("Supervisor puede crear personas", sup.tienePermiso("crear_persona"), "");

        ResultadoPersona alta = ctx.guardarPersona().ejecutar(new ComandoGuardarPersona(
                null, "Prueba Alta Uno", "CC", "9001001", "3001112233", null,
                TipoPersona.INVITADO, null));
        check("Alta de persona", alta.personaId() > 0, "#" + alta.personaId());

        boolean docDuplicado=false; String m="";
        try { ctx.guardarPersona().ejecutar(new ComandoGuardarPersona(
                null,"Otro","CC","9001001",null,null,TipoPersona.INVITADO,null)); }
        catch(Exception e){ docDuplicado=true; m=e.getMessage(); }
        check("Documento duplicado rechazado", docDuplicado, m);

        boolean trabajadorSinEmpresa=false;
        try { new ComandoGuardarPersona(null,"X","CC","9001002",null,null,
                TipoPersona.TRABAJADOR,null); }
        catch(IllegalArgumentException e){ trabajadorSinEmpresa=true; }
        check("Trabajador sin empresa rechazado", trabajadorSinEmpresa, "");

        ctx.guardarPersona().ejecutar(new ComandoGuardarPersona(
                alta.personaId(), "Prueba Alta Editada", "CC", "9001001",
                "3009998877", null, TipoPersona.INVITADO, null));
        var lista = ctx.personas().listar("9001001");
        check("Edicion aplicada", lista.get(0).nombre().equals("Prueba Alta Editada"),
              lista.get(0).nombre());

        System.out.println("\n--- BLOQUEO Y SU EFECTO EN PORTERIA ---");
        // Dos sesiones: el supervisor bloquea, el guarda lo ve. Es la prueba de
        // que la restriccion es efectiva de inmediato en todas las porterias.
        ContextoAplicacion garita = new ContextoAplicacion();
        UsuarioAutenticado g = garita.iniciarSesion()
                .ejecutar(new ComandoLogin("hrincon@zonaacme.co","Guarda123*".toCharArray()));
        garita.sesion().iniciar(g);

        FichaPorteria antes = garita.consultarPorteria()
                .ejecutar(new ComandoConsultarPorteria("CC","9001001"));
        check("Antes del bloqueo NO esta bloqueada",
              antes.veredicto() != Veredicto.BLOQUEADO, antes.veredicto().palabra());

        ctx.cambiarEstadoAcceso().ejecutar(
                ComandoCambiarEstadoAcceso.bloquear(alta.personaId(), "Prueba de bloqueo"));

        FichaPorteria despues = garita.consultarPorteria()
                .ejecutar(new ComandoConsultarPorteria("CC","9001001"));
        check("El guarda ve el bloqueo de inmediato, sin recargar nada",
              despues.veredicto() == Veredicto.BLOQUEADO, despues.veredicto().palabra());

        boolean doble=false;
        try { ctx.cambiarEstadoAcceso().ejecutar(
                ComandoCambiarEstadoAcceso.bloquear(alta.personaId(),"otra vez")); }
        catch(Exception e){ doble=true; }
        check("No se bloquea dos veces", doble, "");

        boolean sinMotivo=false;
        try { ComandoCambiarEstadoAcceso.desbloquear(alta.personaId(), "  "); }
        catch(IllegalArgumentException e){ sinMotivo=true; }
        check("Desbloquear exige motivo", sinMotivo, "trazabilidad en ambos sentidos");

        ctx.cambiarEstadoAcceso().ejecutar(ComandoCambiarEstadoAcceso.desbloquear(
                alta.personaId(), "Aclarado, se levanta la restriccion"));
        check("Desbloqueo efectivo",
              garita.consultarPorteria().ejecutar(new ComandoConsultarPorteria("CC","9001001"))
                 .veredicto() != Veredicto.BLOQUEADO, "");

        System.out.println("\n--- ELIMINACION PROTEGIDA ---");
        ctx.eliminarPersona().ejecutar(new ComandoEliminarPersona(alta.personaId()));
        check("Persona sin historial se elimina",
              ctx.personas().listar("9001001").isEmpty(), "");

        var juliana = ctx.personas().listar("1032998877");
        boolean protegida=false; String mp="";
        try { ctx.eliminarPersona().ejecutar(new ComandoEliminarPersona(juliana.get(0).id())); }
        catch(Exception e){ protegida=true; mp=e.getMessage(); }
        check("Persona CON historial NO se elimina", protegida,
              mp.length()>60 ? mp.substring(0,60)+"..." : mp);

        System.out.println("\n--- RBAC ---");
        boolean frenado=false;
        try { garita.cambiarEstadoAcceso().ejecutar(
                ComandoCambiarEstadoAcceso.bloquear(1L,"x")); }
        catch(com.zonaacme.sica.shared.dominio.ExcepcionPermisoDenegado e){ frenado=true; }
        check("Un guarda NO puede bloquear personas", frenado, "");

        var integridad = ctx.bitacora().verificarIntegridad();
        check("Bitacora integra", integridad.intacta(), integridad.mensaje());

        System.out.println(fallas==0 ? "\n>>> PERSONAS Y DIRECTORIO OK\n" : "\n>>> "+fallas+" FALLAS\n");
        System.exit(fallas==0?0:1);
    }
}
