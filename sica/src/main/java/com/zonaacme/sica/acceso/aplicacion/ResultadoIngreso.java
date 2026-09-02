package com.zonaacme.sica.acceso.aplicacion;

import com.zonaacme.sica.acceso.dominio.EstadoVisita;
import com.zonaacme.sica.acceso.dominio.PersonaEnPorteria;
import com.zonaacme.sica.acceso.dominio.TipoVisita;

/** Lo que ocurrio tras registrar un ingreso. */
public record ResultadoIngreso(
        long              visitaId,
        EstadoVisita      estado,
        TipoVisita        tipo,
        PersonaEnPorteria persona,
        boolean           cerroVisitaOlvidada,
        Long              visitaCerradaId,
        String            mensaje
) {
    /** Si es true, la pantalla del funcionario debe recibir la notificacion. */
    public boolean esperaAprobacion() {
        return estado.esperaAprobacion();
    }

    public boolean entroAlComplejo() {
        return estado == EstadoVisita.DENTRO;
    }
}
