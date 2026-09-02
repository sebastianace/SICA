package com.zonaacme.sica.incidentes.dominio;

/** Categorias de incidente. Los nombres coinciden con el ENUM de la tabla. */
public enum TipoIncidente {
    DOCUMENTO_FALSO("Documento falso o adulterado"),
    COMPORTAMIENTO("Comportamiento inadecuado"),
    OBJETO_PROHIBIDO("Ingreso de objeto prohibido"),
    DANO_PROPIEDAD("Dano a la propiedad"),
    ACCESO_NO_AUTORIZADO("Intento de acceso no autorizado"),
    OTRO("Otro");

    private final String etiqueta;
    TipoIncidente(String etiqueta) { this.etiqueta = etiqueta; }
    public String etiqueta() { return etiqueta; }
}
