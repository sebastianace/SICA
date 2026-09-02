package com.zonaacme.sica.personas.aplicacion;

import com.zonaacme.sica.shared.aplicacion.CasoDeUso;
import com.zonaacme.sica.shared.aplicacion.GestorTransacciones;
import com.zonaacme.sica.shared.dominio.ExcepcionDominio;

/**
 * Alta y edicion de personas.
 *
 * La unicidad del documento se comprueba DENTRO de la transaccion y no antes:
 * comprobar fuera dejaria una ventana entre la consulta y la escritura en la
 * que otro usuario podria insertar el mismo documento. La base de datos tiene
 * ademas la restriccion UNIQUE, que es la garantia real; esta comprobacion
 * existe para poder dar un mensaje entendible en vez de un error de SQL.
 */
public final class GuardarPersonaService
        implements CasoDeUso<ComandoGuardarPersona, ResultadoPersona> {

    private final RepositorioPersonas  repositorio;
    private final GestorTransacciones  transacciones;

    public GuardarPersonaService(RepositorioPersonas repositorio,
                                 GestorTransacciones transacciones) {
        this.repositorio   = repositorio;
        this.transacciones = transacciones;
    }

    @Override
    public ResultadoPersona ejecutar(ComandoGuardarPersona comando) {
        return transacciones.ejecutar(contexto -> {

            if (repositorio.documentoOcupado(contexto,
                    comando.documentoIdentidad().trim(), comando.id())) {
                throw new ExcepcionDominio(
                        "Ya existe otra persona registrada con el documento "
                      + comando.documentoIdentidad() + ".");
            }

            if (comando.esAlta()) {
                long id = repositorio.crear(contexto, comando);
                return new ResultadoPersona(id, comando.nombre(),
                        "Persona registrada correctamente.");
            }

            repositorio.actualizar(contexto, comando);
            return new ResultadoPersona(comando.id(), comando.nombre(),
                    "Datos actualizados.");
        });
    }
}
