package ar.edu.uade.reclamos.aplicacion.prioridad;

import java.time.OffsetDateTime;

/**
 * Pedido de reevaluar la prioridad de un reclamo con la IA, versión 1 del contrato
 * ({@code docs/contratos/mensajes.md}). Solo lleva el número del reclamo: la categoría y la descripción las
 * lee el consumidor de la base, así la descripción no viaja por RabbitMQ.
 *
 * @param intento número de intento, desde 1; el consumidor lo usa para saber cuándo dejar de reintentar
 */
public record MensajeReevaluacion(String eventId, int version, String tipoEvento, String reclamoNumero,
                                  int intento, OffsetDateTime fechaSolicitud) {

    public static final int VERSION = 1;
    public static final String TIPO = "PrioridadPendiente";
}
