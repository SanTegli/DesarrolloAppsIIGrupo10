package ar.edu.uade.reclamos.aplicacion.categoria;

import java.time.OffsetDateTime;

/**
 * Pedido de que la IA sugiera la categoría de un reclamo de "Otro problema", versión 1 del contrato
 * ({@code docs/contratos/mensajes.md}). Como el de la prioridad, solo lleva el número del reclamo: lo que
 * escribió el vecino lo lee el consumidor de la base y no viaja por RabbitMQ.
 *
 * @param intento número de intento, desde 1
 */
public record MensajeSugerenciaCategoria(String eventId, int version, String tipoEvento, String reclamoNumero,
                                         int intento, OffsetDateTime fechaSolicitud) {

    public static final int VERSION = 1;
    public static final String TIPO = "CategoriaPendiente";
}
