package ar.edu.uade.reclamos.notificaciones.aviso;

import java.time.OffsetDateTime;

/** Un aviso tal como llega por RabbitMQ, versión 1 del contrato. Lo valida {@link LectorMensajes}. */
public record MensajeAviso(String eventId, int version, String tipoEvento, String reclamoNumero,
                           Destinatario destinatario, String mensaje, OffsetDateTime fechaEvento) {

    public record Destinatario(Long id, String nombre, String email) { }
}
