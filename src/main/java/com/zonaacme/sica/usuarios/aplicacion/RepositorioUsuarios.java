package com.zonaacme.sica.usuarios.aplicacion;

import java.util.Optional;

public interface RepositorioUsuarios {

    Optional<CredencialUsuario> buscarPorUsername(String username);

    void registrarAccesoExitoso(long usuarioId);

    void registrarIntentoFallido(String username);
}
