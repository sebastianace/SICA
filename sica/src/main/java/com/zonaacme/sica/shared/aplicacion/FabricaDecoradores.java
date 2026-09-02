package com.zonaacme.sica.shared.aplicacion;

import com.zonaacme.sica.shared.auditoria.Bitacora;
import com.zonaacme.sica.shared.seguridad.SesionActual;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * PATRON BUILDER sobre los decoradores.
 *
 * Compone las capas en el orden correcto y esconde ese orden del resto del
 * sistema. Uso tipico en el ensamblador de la aplicacion:
 *
 *   CasoDeUso&lt;ComandoIngreso, ResultadoIngreso&gt; registrarIngreso =
 *       fabrica.envolver(new RegistrarIngresoService(...))
 *              .conPermiso("registrar_visita")
 *              .auditando("REGISTRAR_INGRESO", "visita",
 *                         (cmd, res) -> DescripcionAuditoria.de(
 *                              res.visitaId(),
 *                              "Ingreso de " + res.nombrePersona()))
 *              .construir();
 *
 * El servicio de negocio no sabe que existe la auditoria ni el RBAC.
 */
public final class FabricaDecoradores {

    private final SesionActual sesion;
    private final Bitacora bitacora;

    public FabricaDecoradores(SesionActual sesion, Bitacora bitacora) {
        this.sesion = sesion;
        this.bitacora = bitacora;
    }

    public <E, S> Constructor<E, S> envolver(CasoDeUso<E, S> base) {
        return new Constructor<>(base, sesion, bitacora);
    }

    public static final class Constructor<E, S> {

        private final CasoDeUso<E, S> base;
        private final SesionActual sesion;
        private final Bitacora bitacora;

        private String permiso;
        private String accion;
        private String entidad;
        private BiFunction<E, S, DescripcionAuditoria> descriptorExito;
        private Function<E, String> descriptorFallo = entrada -> "Entrada: " + entrada;

        private Constructor(CasoDeUso<E, S> base, SesionActual sesion, Bitacora bitacora) {
            this.base = base;
            this.sesion = sesion;
            this.bitacora = bitacora;
        }

        public Constructor<E, S> conPermiso(String codigoPermiso) {
            this.permiso = codigoPermiso;
            return this;
        }

        public Constructor<E, S> auditando(String accion, String entidad,
                                           BiFunction<E, S, DescripcionAuditoria> descriptorExito) {
            this.accion = accion;
            this.entidad = entidad;
            this.descriptorExito = descriptorExito;
            return this;
        }

        public Constructor<E, S> describiendoFallo(Function<E, String> descriptorFallo) {
            this.descriptorFallo = descriptorFallo;
            return this;
        }

        /**
         * El orden de envoltura no es arbitrario:
         *   auditoria ( seguridad ( servicio ) )
         * De adentro hacia afuera se aplica primero seguridad, de modo que la
         * auditoria queda por fuera y alcanza a registrar tambien los intentos
         * denegados por falta de permisos.
         */
        public CasoDeUso<E, S> construir() {
            CasoDeUso<E, S> resultado = base;

            if (permiso != null) {
                resultado = new SeguridadDecorator<>(resultado, permiso, sesion);
            }
            if (accion != null) {
                resultado = new AuditoriaDecorator<>(resultado, bitacora, sesion,
                        accion, entidad, descriptorExito, descriptorFallo);
            }
            return resultado;
        }
    }
}
