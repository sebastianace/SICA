package com.zonaacme.sica.usuarios.aplicacion;

import java.util.Set;

/**
 * Lo que el repositorio devuelve para poder validar un login: los datos del
 * usuario, su hash y sus permisos ya resueltos desde rol_permiso.
 * El hash no sale nunca de la capa de aplicacion.
 */
public record CredencialUsuario(
        long        id,
        String      username,
        String      passwordHash,
        String      nombreCompleto,
        String      rol,
        Long        empresaId,
        boolean     activo,
        Set<String> permisos
) { }
