import com.zonaacme.sica.arranque.ContextoAplicacion;
import com.zonaacme.sica.usuarios.aplicacion.ComandoLogin;
import com.zonaacme.sica.acceso.aplicacion.*;
import com.zonaacme.sica.acceso.dominio.*;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;
import java.util.concurrent.*;

public class PruebaFlujo2 {
    static int fallas = 0;
    static void check(String e, boolean ok, String d) {
        System.out.printf("  %-50s %-6s %s%n", e, ok?"OK":"FALLA", d);
        if(!ok) fallas++;
    }
    public static void main(String[] a) throws Exception {
        // Dos contextos = dos ventanas abiertas, cada una con su sesion.
        ContextoAplicacion garita     = new ContextoAplicacion();
        ContextoAplicacion escritorio = new ContextoAplicacion();

        System.out.println("\n--- FLUJO 2: INVITADO NO ANUNCIADO ---");
        UsuarioAutenticado guarda = garita.iniciarSesion()
                .ejecutar(new ComandoLogin("hrincon@zonaacme.co","Guarda123*".toCharArray()));
        garita.sesion().iniciar(guarda);
        UsuarioAutenticado func = escritorio.iniciarSesion()
                .ejecutar(new ComandoLogin("mrodriguez@nexoanalytics.co","Funciona123*".toCharArray()));
        escritorio.sesion().iniciar(func);

        // El funcionario esta mirando su bandeja: se suscribe al bus.
        CountDownLatch avisoRecibido = new CountDownLatch(1);
        var capturado = new java.util.concurrent.atomic.AtomicReference<SolicitudDeIngresoCreada>();
        escritorio.bus().suscribir(SolicitudDeIngresoCreada.class, ev -> {
            capturado.set(ev); avisoRecibido.countDown();
        });
        // El guarda espera la respuesta.
        CountDownLatch respuestaRecibida = new CountDownLatch(1);
        var resuelto = new java.util.concurrent.atomic.AtomicReference<SolicitudDeIngresoResuelta>();
        garita.bus().suscribir(SolicitudDeIngresoResuelta.class, ev -> {
            resuelto.set(ev); respuestaRecibida.countDown();
        });

        // Valentina Nieto (INVITADO sin visita previa) llega a la porteria.
        FichaPorteria ficha = garita.consultarPorteria()
                .ejecutar(new ComandoConsultarPorteria("CC","1088776655"));
        check("Invitada sin cita requiere autorizacion",
              ficha.veredicto()==Veredicto.REQUIERE_AUTORIZACION, ficha.veredicto().palabra());

        ResultadoIngreso sol = garita.registrarIngreso().ejecutar(
                ComandoRegistrarIngreso.invitadoNoAnunciado("CC","1088776655",
                        1L, func.id(), "Reunion comercial"));
        check("Solicitud creada en espera", sol.esperaAprobacion(),
              "visita #" + sol.visitaId() + " estado " + sol.estado().name());

        check("TIEMPO REAL: el funcionario fue notificado",
              avisoRecibido.await(3, TimeUnit.SECONDS),
              capturado.get()==null?"":"llego: " + capturado.get().nombrePersona());

        // El funcionario ve la solicitud en su bandeja.
        var bandeja = escritorio.visitas().pendientesDeEmpresa(1L);
        check("Aparece en la bandeja del funcionario",
              bandeja.stream().anyMatch(s -> s.visitaId()==sol.visitaId()),
              bandeja.size() + " pendientes");

        // Aprueba.
        ResultadoResolucion res = escritorio.resolverSolicitud()
                .ejecutar(ComandoResolverSolicitud.aprobar(sol.visitaId()));
        check("Funcionario aprueba", res.aprobada(), res.mensaje());

        check("TIEMPO REAL: la garita fue notificada",
              respuestaRecibida.await(3, TimeUnit.SECONDS),
              resuelto.get()==null?"":"aprobada=" + resuelto.get().fueAprobada());
        check("El evento trae el personaId (no 0)",
              resuelto.get()!=null && resuelto.get().personaId() > 0,
              resuelto.get()==null?"":"personaId=" + resuelto.get().personaId());
        check("El guarda sabe a quien corresponde la respuesta",
              resuelto.get()!=null && resuelto.get().personaId()==ficha.persona().id(),
              "coincide con la persona en pantalla");

        // Ahora si puede entrar.
        FichaPorteria tras = garita.consultarPorteria()
                .ejecutar(new ComandoConsultarPorteria("CC","1088776655"));
        check("Ya aparece AUTORIZADO en porteria",
              tras.veredicto()==Veredicto.AUTORIZADO, tras.veredicto().palabra());

        ResultadoIngreso ent = garita.registrarIngreso().ejecutar(
                ComandoRegistrarIngreso.checkInDeVisitaAprobada("CC","1088776655", sol.visitaId()));
        check("Check-in efectivo", ent.estado()==EstadoVisita.DENTRO, ent.estado().name());

        System.out.println("\n--- CONDICION DE CARRERA: doble respuesta ---");
        boolean segundaRechazada = false; String msg="";
        try { escritorio.resolverSolicitud()
                .ejecutar(ComandoResolverSolicitud.aprobar(sol.visitaId())); }
        catch (Exception e) { segundaRechazada = true; msg = e.getMessage(); }
        check("Resolver dos veces la misma solicitud falla", segundaRechazada, msg);

        System.out.println("\n--- RBAC ---");
        boolean guardaFrenado = false;
        try { garita.resolverSolicitud().ejecutar(ComandoResolverSolicitud.aprobar(sol.visitaId())); }
        catch (com.zonaacme.sica.shared.dominio.ExcepcionPermisoDenegado e) { guardaFrenado = true; }
        check("Un guarda NO puede autorizar su propia solicitud", guardaFrenado, "");

        var integridad = escritorio.bitacora().verificarIntegridad();
        check("Bitacora integra tras todo el flujo", integridad.intacta(), integridad.mensaje());

        System.out.println(fallas==0 ? "\n>>> FLUJO 2 COMPLETO\n" : "\n>>> "+fallas+" FALLAS\n");
        System.exit(fallas==0?0:1);
    }
}
