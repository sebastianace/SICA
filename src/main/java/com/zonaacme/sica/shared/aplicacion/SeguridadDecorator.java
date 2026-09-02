package com.zonaacme.sica.shared.aplicacion;

import com.zonaacme.sica.shared.dominio.ExcepcionPermisoDenegado;
import com.zonaacme.sica.shared.seguridad.SesionActual;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;

/**
 * PATRON DECORATOR - control de acceso basado en roles.
 *
 * Envuelve cualquier caso de uso y verifica el permiso ANTES de delegar.
 *
 * El valor de resolverlo asi: ningun servicio de negocio contiene una sola
 * linea de autorizacion. RegistrarIngresoService sabe registrar ingresos y
 * nada mas. Agregar una operacion nueva al sistema no obliga a modificar este
 * decorador, y cambiar la politica de autorizacion no obliga a tocar ningun
 * servicio. Eso es Open/Closed y Single Responsibility al mismo tiempo.
 *
 * El permiso requerido llega como String porque los permisos son datos que
 * viven en la tabla 'permiso', no constantes del codigo.
 */
public final class SeguridadDecorator<E, S> implements CasoDeUso<E, S> {

    private final CasoDeUso<E, S> delegado;
    private final String permisoRequerido;
    private final SesionActual sesion;

    public SeguridadDecorator(CasoDeUso<E, S> delegado, String permisoRequerido, SesionActual sesion) {
        this.delegado = delegado;
        this.permisoRequerido = permisoRequerido;
        this.sesion = sesion;
    }

    @Override
    public S ejecutar(E entrada) {
        UsuarioAutenticado usuario = sesion.obligatorio();
        if (!usuario.tienePermiso(permisoRequerido)) {
            throw new ExcepcionPermisoDenegado(permisoRequerido);
        }
        return delegado.ejecutar(entrada);
    }
}
