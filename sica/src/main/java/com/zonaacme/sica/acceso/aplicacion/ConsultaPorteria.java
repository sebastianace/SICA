package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.PersonaEnPorteria;
import com.zonaacme.sica.shared.aplicacion.ContextoTransaccion;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida propio del slice de acceso para leer personas.
 *
 * El slice de acceso NO importa la entidad Persona del slice de personas.
 * Declara lo que necesita y deja que un adaptador se lo entregue. Asi los dos
 * slices pueden cambiar por dentro sin romperse mutuamente.
 */
public interface ConsultaPorteria {

    /** Busqueda exacta fuera de transaccion, para pintar la pantalla. */
    Optional<PersonaEnPorteria> buscarPorDocumento(String tipoDocumento, String numeroDocumento);

    /** Busqueda exacta dentro de la transaccion de ingreso. */
    Optional<PersonaEnPorteria> buscarPorDocumento(ContextoTransaccion contexto,
                                                   String tipoDocumento, String numeroDocumento);

    /** Busqueda difusa por documento parcial, nombres o apellidos. */
    List<PersonaEnPorteria> buscar(String texto, int limite);

    /**
     * Nombre de una persona por su id, para armar mensajes.
     * Va dentro de la transaccion porque se usa al resolver una solicitud.
     */
    String nombreDePersona(ContextoTransaccion contexto, long personaId);
}
