package com.zonaacme.sica.usuarios.aplicacion;

import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.auditoria.Bitacora;
import com.zonaacme.sica.shared.auditoria.RegistroAuditoria;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;
import com.zonaacme.sica.shared.seguridad.HasheadorPassword;
import com.zonaacme.sica.shared.seguridad.SesionActual;
import com.zonaacme.sica.shared.seguridad.UsuarioAutenticado;

import java.util.Arrays;
import java.util.Optional;

/**
 * Autenticacion.
 *
 * Es el UNICO caso de uso que escribe en la bitacora por su cuenta, en vez de
 * dejarselo al decorador. La razon es honesta y hay que poder explicarla:
 *
 *  - El decorador de seguridad no aplica: todavia no hay sesion, y exigir un
 *    permiso para iniciar sesion seria un circulo.
 *  - El decorador de auditoria tampoco encaja: necesita un usuarioId de la
 *    sesion, y en un login fallido no existe. El enunciado pide registrar los
 *    intentos fallidos, asi que la bitacora aqui guarda el username tecleado en
 *    la columna usuario_intento.
 *
 * Que la excepcion este documentada y acotada a un solo caso es lo que la hace
 * una decision de diseno y no una inconsistencia.
 *
 * El mensaje de error es identico para usuario inexistente y para clave
 * incorrecta, a proposito: decir cual de los dos fallo le confirma a un
 * atacante que ese usuario existe.
 */
public final class IniciarSesionService implements CasoDeUso<ComandoLogin, UsuarioAutenticado> {

    private static final String MENSAJE_GENERICO = "Usuario o contrasena incorrectos.";

    private final RepositorioUsuarios repositorio;
    private final Bitacora bitacora;
    private final SesionActual sesion;

    public IniciarSesionService(RepositorioUsuarios repositorio, Bitacora bitacora, SesionActual sesion) {
        this.repositorio = repositorio;
        this.bitacora = bitacora;
        this.sesion = sesion;
    }

    @Override
    public UsuarioAutenticado ejecutar(ComandoLogin comando) {
        try {
            Optional<CredencialUsuario> encontrado = repositorio.buscarPorUsername(comando.username());

            if (encontrado.isEmpty()) {
                registrarFallo(comando.username(), null, "El usuario no existe.");
                throw new ExcepcionDominio(MENSAJE_GENERICO);
            }

            CredencialUsuario credencial = encontrado.get();

            if (!credencial.activo()) {
                registrarFallo(comando.username(), credencial.id(), "La cuenta esta desactivada.");
                throw new ExcepcionDominio("Esta cuenta esta desactivada. Habla con el administrador.");
            }

            if (!HasheadorPassword.verificar(comando.password(), credencial.passwordHash())) {
                repositorio.registrarIntentoFallido(comando.username());
                registrarFallo(comando.username(), credencial.id(), "Contrasena incorrecta.");
                throw new ExcepcionDominio(MENSAJE_GENERICO);
            }

            UsuarioAutenticado usuario = new UsuarioAutenticado(
                    credencial.id(), credencial.username(), credencial.nombreCompleto(),
                    credencial.rol(), credencial.empresaId(), credencial.permisos());

            sesion.iniciar(usuario);
            repositorio.registrarAccesoExitoso(credencial.id());

            bitacora.registrar(RegistroAuditoria.intentoLogin(
                    comando.username(), true, credencial.id(),
                    "Sesion iniciada con rol " + credencial.rol()
                  + " y " + credencial.permisos().size() + " permisos.",
                    sesion.terminal()));

            return usuario;

        } finally {
            // La contrasena no se queda en memoria mas de lo necesario.
            Arrays.fill(comando.password(), '\0');
        }
    }

    private void registrarFallo(String username, Long usuarioId, String motivo) {
        bitacora.registrar(RegistroAuditoria.intentoLogin(
                username, false, usuarioId, motivo, sesion.terminal()));
    }
}
