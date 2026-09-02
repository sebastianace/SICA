package com.zonaacme.sica.directorio.aplicacion;

import java.util.List;

/**
 * Puerto de consulta del directorio del complejo: empresas y sus anfitriones.
 *
 * Es un puerto propio y no un metodo mas dentro de los repositorios existentes
 * porque lo consumen pantallas de slices distintos: la porteria necesita saber
 * a que empresa va un visitante, y la administracion necesita la misma lista
 * para sus formularios. Meterlo en RepositorioVisitas obligaria a la
 * administracion a depender de un puerto de escritura de visitas que no usa.
 */
public interface Directorio {

    List<EmpresaBreve> empresasActivas();

    /** Funcionarios de una empresa que pueden recibir solicitudes de ingreso. */
    List<AnfitrionBreve> anfitrionesDe(long empresaId);
}
