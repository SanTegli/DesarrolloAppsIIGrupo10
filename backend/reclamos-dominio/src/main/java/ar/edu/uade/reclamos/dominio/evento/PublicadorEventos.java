package ar.edu.uade.reclamos.dominio.evento;

/**
 * Patrón Observer: los servicios publican eventos por esta interfaz y no conocen a quienes reaccionan.
 *
 * <p>En el Hito 1 la implementa {@code PublicadorEventosSpring} con eventos internos de Spring.
 * En la Segunda Parte los avisos se envían además por RabbitMQ sin cambiar los servicios.
 */
public interface PublicadorEventos {

    void publicar(EventoDominio evento);
}
