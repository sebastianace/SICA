package com.zonaacme.sica.acceso.dominio;

/**
 * La decision que el guarda necesita leer de un vistazo, desde el otro lado de
 * la caseta. Cada valor trae su propia palabra y su propio color: la pantalla
 * no decide nada, solo pinta lo que el dominio ya resolvio.
 */
public enum Veredicto {

    AUTORIZADO           ("AUTORIZADO",   "verde",  "Puede ingresar. Registra el check-in."),
    REQUIERE_AUTORIZACION("AUTORIZAR",    "ambar",  "Sin visita aprobada. Hay que pedir autorizacion al anfitrion."),
    PENDIENTE            ("EN ESPERA",    "ambar",  "La solicitud ya fue enviada y espera respuesta del anfitrion."),
    BLOQUEADO            ("BLOQUEADO",    "rojo",   "Restriccion de acceso activa. No permitir el ingreso."),
    NO_REGISTRADO        ("SIN REGISTRO", "neutro", "La persona no existe en el sistema. Hay que registrarla primero.");

    private final String palabra;
    private final String colorCss;
    private final String instruccion;

    Veredicto(String palabra, String colorCss, String instruccion) {
        this.palabra = palabra;
        this.colorCss = colorCss;
        this.instruccion = instruccion;
    }

    public String palabra()     { return palabra; }
    public String colorCss()    { return colorCss; }
    public String instruccion() { return instruccion; }

    public boolean permiteIngreso() {
        return this == AUTORIZADO || this == REQUIERE_AUTORIZACION;
    }
}
