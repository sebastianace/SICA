package com.zonaacme.sica.shared.eventos;

import com.zonaacme.sica.shared.dominio.EventoDominio;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Implementacion del patron Observer sobre hilos.
 *
 * Decisiones de concurrencia:
 *
 *  - ConcurrentHashMap para el registro de suscriptores: varias ventanas se
 *    suscriben desde hilos distintos al abrirse.
 *
 *  - CopyOnWriteArrayList para la lista de manejadores de cada tipo: se lee
 *    muchisimo mas de lo que se escribe (una suscripcion por ventana, miles de
 *    publicaciones) y ademas evita ConcurrentModificationException si alguien
 *    se suscribe mientras se esta despachando.
 *
 *  - ExecutorService con hilos demonio: quien publica no se queda esperando a
 *    que todos los suscriptores terminen. El guarda registra el ingreso y su
 *    pantalla responde de inmediato, aunque la del funcionario tarde.
 *    Demonio para que cerrar la aplicacion no deje la JVM colgada.
 */
public final class BusEventosEnMemoria implements PublicadorEventos {

    private final Map<Class<?>, List<Consumer<EventoDominio>>> suscriptores = new ConcurrentHashMap<>();

    private final ExecutorService despachador = Executors.newFixedThreadPool(2, tarea -> {
        Thread hilo = new Thread(tarea, "sica-eventos");
        hilo.setDaemon(true);
        return hilo;
    });

    @Override
    public void publicar(EventoDominio evento) {
        List<Consumer<EventoDominio>> destinatarios = suscriptores.get(evento.getClass());
        if (destinatarios == null || destinatarios.isEmpty()) {
            return;
        }
        destinatarios.forEach(manejador -> despachador.submit(() -> {
            try {
                manejador.accept(evento);
            } catch (RuntimeException fallo) {
                // Un suscriptor roto no puede tumbar a los demas ni al publicador.
                System.err.println("[bus] fallo un suscriptor de "
                        + evento.getClass().getSimpleName() + ": " + fallo.getMessage());
            }
        }));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends EventoDominio> void suscribir(Class<T> tipoEvento, Consumer<T> manejador) {
        suscriptores
            .computeIfAbsent(tipoEvento, tipo -> new CopyOnWriteArrayList<>())
            .add((Consumer<EventoDominio>) manejador);
    }

    public void apagar() {
        despachador.shutdownNow();
    }
}
