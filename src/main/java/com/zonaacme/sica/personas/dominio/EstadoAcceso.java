package com.zonaacme.sica.personas.dominio;

/**
 * Estado de acceso de una persona.
 *
 * Es el interruptor del requisito "bloquear_persona" del enunciado. Al ser una
 * columna de la tabla personas, la restriccion es efectiva de inmediato en
 * TODOS los puntos de entrada, porque todos consultan la misma fila. No hay que
 * avisar por radio a cada garita, que es justamente el problema que el
 * enunciado describe del proceso viejo.
 */
public enum EstadoAcceso {
    PERMITIDO("Activo"),
    BLOQUEADO("Con Prohibicion de Ingreso");

    private final String valorEnBd;
    EstadoAcceso(String valorEnBd) { this.valorEnBd = valorEnBd; }

    public String valorEnBd() { return valorEnBd; }

    public boolean permiteIngreso() { return this == PERMITIDO; }

    public static EstadoAcceso desdeBd(String valor) {
        return BLOQUEADO.valorEnBd.equalsIgnoreCase(valor) ? BLOQUEADO : PERMITIDO;
    }

    @Override public String toString() { return valorEnBd; }
}
