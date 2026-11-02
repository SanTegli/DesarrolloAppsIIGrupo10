package ar.edu.uade.reclamos.aplicacion.mensajeria;

import java.time.OffsetDateTime;

/**
 * El mensaje que sale por RabbitMQ, versión 1 del contrato ({@code docs/contratos/mensajes.md}). No lleva la
 * descripción del reclamo, ni DNI, ni teléfono.
 */
public record MensajeAviso(String eventId, int version, String tipoEvento, String reclamoNumero,
                           Destinatario destinatario, String mensaje, OffsetDateTime fechaEvento) {

    public static final int VERSION = 1;

    /** Quién recibe el correo. */
    public record Destinatario(Long id, String nombre, String email) { }
}
