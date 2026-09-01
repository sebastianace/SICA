package com.zonaacme.sica.acceso.dominio;

import com.zonaacme.sica.shared.dominio.ExcepcionDominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * PATRON STATE, en su forma idiomatica en Java: un enum que conoce sus propias
 * transiciones validas.
 *
 * Aqui vive la regla mas importante del sistema. Ningun servicio puede mover
 * una visita a un estado arbitrario: tiene que pedirselo al estado actual, y el
 * estado actual decide si es legal. Un DENTRO no puede volver a APROBADA, y una
 * visita FINALIZADA no puede reabrirse.
 *
 * Se prefirio el enum sobre una jerarquia de clases State porque el conjunto de
 * estados es cerrado y esta definido en el ENUM de la columna 'estado' de MySQL.
 * Una clase por estado agregaria ocho archivos sin agregar ni una regla nueva.
 */
public enum EstadoVisita {

    APROBADA                    ("Aprobada",              Severidad.POSITIVA),
    PENDIENTE_APROBACION        ("Pendiente",             Severidad.ESPERA),
    PENDIENTE_APROBACION_OLVIDO ("Pendiente por olvido",  Severidad.ESPERA),
    RECHAZADA                   ("Rechazada",             Severidad.NEGATIVA),
    DENTRO                      ("Dentro",                Severidad.POSITIVA),
    FINALIZADA                  ("Finalizada",            Severidad.NEUTRA),
    CERRADA_POR_SISTEMA         ("Cerrada por el sistema",Severidad.ADVERTENCIA),
    EXPIRADA                    ("Expirada",              Severidad.NEUTRA);

    public enum Severidad { POSITIVA, ESPERA, NEGATIVA, ADVERTENCIA, NEUTRA }

    private final String etiqueta;
    private final Severidad severidad;

    EstadoVisita(String etiqueta, Severidad severidad) {
        this.etiqueta = etiqueta;
        this.severidad = severidad;
    }

    /**
     * Mapa completo de la maquina de estados. Un cambio de reglas de negocio se
     * hace aqui y en ningun otro lugar.
     */
    public Set<EstadoVisita> transicionesPermitidas() {
        return switch (this) {
            case APROBADA ->
                    EnumSet.of(DENTRO, RECHAZADA, EXPIRADA);

            case PENDIENTE_APROBACION, PENDIENTE_APROBACION_OLVIDO ->
                    EnumSet.of(APROBADA, RECHAZADA, EXPIRADA);

            case DENTRO ->
                    EnumSet.of(FINALIZADA, CERRADA_POR_SISTEMA);

            // Estados terminales: de aqui no se sale.
            case RECHAZADA, FINALIZADA, CERRADA_POR_SISTEMA, EXPIRADA ->
                    EnumSet.noneOf(EstadoVisita.class);
        };
    }

    public boolean puedeTransicionarA(EstadoVisita destino) {
        return transicionesPermitidas().contains(destino);
    }

    public void validarTransicionA(EstadoVisita destino) {
        if (!puedeTransicionarA(destino)) {
            throw new ExcepcionDominio(
                    "Transicion invalida: una visita en estado '" + etiqueta
                  + "' no puede pasar a '" + destino.etiqueta + "'.");
        }
    }

    /** Estado en el que la persona esta fisicamente dentro del complejo. */
    public boolean estaAbierta() {
        return this == DENTRO;
    }

    /** Espera la decision de un funcionario de empresa. */
    public boolean esperaAprobacion() {
        return this == PENDIENTE_APROBACION || this == PENDIENTE_APROBACION_OLVIDO;
    }

    public boolean esTerminal() {
        return transicionesPermitidas().isEmpty();
    }

    public String etiqueta()      { return etiqueta; }
    public Severidad severidad()  { return severidad; }
}
