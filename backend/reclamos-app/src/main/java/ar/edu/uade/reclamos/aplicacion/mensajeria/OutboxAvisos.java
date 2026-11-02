package ar.edu.uade.reclamos.aplicacion.mensajeria;

import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * <b>Outbox</b>: en lugar de publicar en RabbitMQ en medio del caso de uso, guarda el mensaje en la tabla
 * {@code mensajes_salientes}, dentro de la misma transacción que el aviso. Si la transacción se revierte, el
 * mensaje tampoco existe; si confirma, el mensaje queda aunque el broker esté caído.
 * {@link PublicadorMensajes} lo publica después.
 */
public class OutboxAvisos implements SalidaDeAvisos {

    private final MensajeSalienteRepository mensajes;
    private final ObjectMapper json;
    private final Clock reloj;

    public OutboxAvisos(MensajeSalienteRepository mensajes, ObjectMapper json, Clock reloj) {
        this.mensajes = mensajes;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    public void enviar(Notificacion aviso, TipoAviso tipo) {
        String eventId = UUID.randomUUID().toString();
        Usuario destinatario = aviso.getDestinatario();
        MensajeAviso mensaje = new MensajeAviso(eventId, MensajeAviso.VERSION, tipo.evento(),
                aviso.getReclamo().getNumero(),
                new MensajeAviso.Destinatario(destinatario.getId(), destinatario.getNombreCompleto(),
                        destinatario.getEmail()),
                aviso.getMensaje(),
                OffsetDateTime.of(aviso.getFechaEnvio(), reloj.getZone().getRules().getOffset(aviso.getFechaEnvio())));
        mensajes.guardar(new MensajeSaliente(eventId, tipo.claveRuteo(), tipo.evento(), serializar(mensaje),
                aviso.getFechaEnvio()));
    }

    private String serializar(MensajeAviso mensaje) {
        try {
            return json.writeValueAsString(mensaje);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo armar el mensaje del aviso", error);
        }
    }
}
