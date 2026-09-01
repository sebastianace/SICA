package com.zonaacme.sica.shared.aplicacion;

import com.zonaacme.sica.shared.auditoria.Bitacora;
import com.zonaacme.sica.shared.auditoria.RegistroAuditoria;
import com.zonaacme.sica.shared.seguridad.SesionActual;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * PATRON DECORATOR - bitacora de auditoria.
 *
 * El enunciado exige que la bitacora se alimente "desde la capa de servicio de
 * Java". Este decorador ES esa capa, y cumple el requisito sin ensuciar ni una
 * sola regla de negocio con codigo de auditoria.
 *
 * Se coloca POR FUERA del decorador de seguridad. El orden importa: asi un
 * intento de ejecutar algo sin permiso tambien queda registrado como FALLO, que
 * es justamente el evento que un auditor de seguridad quiere ver.
 *
 * Como se describe cada operacion se recibe en forma de lambda, para que el
 * decorador no tenga que conocer los tipos concretos de ningun caso de uso.
 */
public final class AuditoriaDecorator<E, S> implements CasoDeUso<E, S> {

    private final CasoDeUso<E, S> delegado;
    private final Bitacora bitacora;
    private final SesionActual sesion;
    private final String accion;
    private final String entidad;
    private final BiFunction<E, S, DescripcionAuditoria> descriptorExito;
    private final Function<E, String> descriptorFallo;

    public AuditoriaDecorator(CasoDeUso<E, S> delegado,
                              Bitacora bitacora,
                              SesionActual sesion,
                              String accion,
                              String entidad,
                              BiFunction<E, S, DescripcionAuditoria> descriptorExito,
                              Function<E, String> descriptorFallo) {
        this.delegado = delegado;
        this.bitacora = bitacora;
        this.sesion = sesion;
        this.accion = accion;
        this.entidad = entidad;
        this.descriptorExito = descriptorExito;
        this.descriptorFallo = descriptorFallo;
    }

    @Override
    public S ejecutar(E entrada) {
        Long usuarioId = sesion.haySesion() ? sesion.usuario().id() : null;
        String terminal = sesion.terminal();
        try {
            S salida = delegado.ejecutar(entrada);

            DescripcionAuditoria descripcion = descriptorExito.apply(entrada, salida);
            bitacora.registrar(RegistroAuditoria.exito(
                    usuarioId, accion, entidad,
                    descripcion.idEntidad(), descripcion.detalle(), terminal));

            return salida;

        } catch (RuntimeException fallo) {
            String motivo = descriptorFallo.apply(entrada) + " | Motivo: " + fallo.getMessage();
            bitacora.registrar(RegistroAuditoria.fallo(
                    usuarioId, accion, entidad, null, motivo, terminal));
            throw fallo;
        }
    }
}
