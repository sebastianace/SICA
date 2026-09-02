package com.zonaacme.sica.shared.seguridad;

import java.util.Collections;
import java.util.Set;

/**
 * Instantanea inmutable del usuario que abrio sesion, con sus permisos ya
 * resueltos desde la base de datos.
 *
 * El conjunto de permisos se carga en el login y no se vuelve a consultar
 * durante la sesion. Es una decision consciente: evita una consulta por cada
 * accion, a cambio de que un cambio de permisos aplique en el siguiente ingreso.
 */
public final class UsuarioAutenticado {

    private final long id;
    private final String username;
    private final String nombreCompleto;
    private final String rol;
    private final Long empresaId;
    private final Set<String> permisos;

    public UsuarioAutenticado(long id, String username, String nombreCompleto,
                              String rol, Long empresaId, Set<String> permisos) {
        this.id = id;
        this.username = username;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.empresaId = empresaId;
        this.permisos = Collections.unmodifiableSet(permisos);
    }

    /**
     * Unica forma valida de preguntar por autorizacion en todo el sistema.
     * No existe ningun  if (rol.equals("ADMIN"))  en el codigo: los permisos
     * viven en las tablas permiso / rol_permiso.
     */
    public boolean tienePermiso(String codigoPermiso) {
        return permisos.contains(codigoPermiso);
    }

    public long id()                { return id; }
    public String username()        { return username; }
    public String nombreCompleto()  { return nombreCompleto; }
    public String rol()             { return rol; }
    public Long empresaId()         { return empresaId; }
    public Set<String> permisos()   { return permisos; }
}
