package com.zonaacme.sica.usuarios.aplicacion;

/**
 * Credenciales de inicio de sesion.
 *
 * El identificador es el correo, que es la columna unica de la tabla usuarios
 * en el modelo de datos. El componente conserva el nombre generico username
 * porque a la capa de aplicacion no le corresponde saber si el sistema
 * identifica a la gente por correo, por cedula o por nombre de cuenta: eso es
 * una decision del modelo de datos y del adaptador que lo consulta.
 */
public record ComandoLogin(String username, char[] password) { }
