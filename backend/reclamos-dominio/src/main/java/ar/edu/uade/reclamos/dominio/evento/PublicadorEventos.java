package ar.edu.uade.reclamos.dominio.evento;

/**
 * Patrón Observer: los servicios publican eventos por esta interfaz y no conocen a quienes reaccionan.
 *
 * <p>En el Hito 1 la implementa {@code PublicadorEventosSpring} (PR 3) con eventos internos de Spring.
 * En el Hito 2 se agrega la publicación en el broker sin cambiar los servicios.
 */
public interface PublicadorEventos {

    void publicar(EventoDominio evento);
}
