package com.zonaacme.sica.acceso.dominio;

/**
 * Modelo de lectura propio del slice de acceso.
 *
 * NO es la entidad Persona del slice de personas. Es solo lo que la porteria
 * necesita ver en pantalla para decidir en dos segundos. Que cada slice defina
 * su propia vista de los datos es lo que les permite evolucionar por separado:
 * si manana el slice de personas agrega diez campos nuevos, esta pantalla no
 * cambia.
 */
public record PersonaEnPorteria(
        long    id,
        String  tipoDocumento,
        String  numeroDocumento,
        String  nombreCompleto,
        String  tipoPersona,
        Long    empresaId,
        String  empresaNombre,
        String  fotoUrl,
        boolean bloqueada,
        String  motivoBloqueo
) {
    public boolean esTrabajador() {
        return "TRABAJADOR".equals(tipoPersona) || "CONTRATISTA".equals(tipoPersona);
    }

    public String documentoFormateado() {
        return tipoDocumento + " " + numeroDocumento;
    }
}
