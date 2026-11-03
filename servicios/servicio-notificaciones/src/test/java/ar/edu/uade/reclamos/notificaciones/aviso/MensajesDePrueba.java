package ar.edu.uade.reclamos.notificaciones.aviso;

import java.util.UUID;

/** Mensajes con la forma del contrato, para los tests. */
final class MensajesDePrueba {

    private MensajesDePrueba() {
    }

    static String valido(String eventId) {
        return """
                {"eventId": "%s", "version": 1, "tipoEvento": "ReclamoAsignado",
                 "reclamoNumero": "REC-4F2A91BC",
                 "destinatario": {"id": 1, "nombre": "Ana Pérez", "email": "ana@example.org"},
                 "mensaje": "Tu reclamo REC-4F2A91BC fue asignado al area Alumbrado.",
                 "fechaEvento": "2026-10-26T15:00:00-03:00", "campoNuevo": "se ignora"}""".formatted(eventId);
    }

    static String valido() {
        return valido(UUID.randomUUID().toString());
    }
}
