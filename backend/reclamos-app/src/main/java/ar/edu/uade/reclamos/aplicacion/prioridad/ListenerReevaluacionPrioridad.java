package ar.edu.uade.reclamos.aplicacion.prioridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Consumidor de {@code reclamos.prioridad}. Lee el pedido y se lo pasa a {@link ServicioReevaluacionPrioridad}.
 * Si el servicio termina bien (aunque haya programado otro intento), Spring confirma el mensaje.
 *
 * <p>Un mensaje ilegible o de otra versión va directo a {@code reclamos.prioridad.dlq}. Un error inesperado
 * (por ejemplo, la base caída) se reintenta unas pocas veces con la configuración del listener y después
 * también termina en la cola de errores.
 */
public class ListenerReevaluacionPrioridad {

    private final ServicioReevaluacionPrioridad servicio;
    private final ObjectMapper json;

    public ListenerReevaluacionPrioridad(ServicioReevaluacionPrioridad servicio, ObjectMapper json) {
        this.servicio = servicio;
        this.json = json;
    }

    @RabbitListener(queues = TopologiaReevaluacion.COLA)
    public void recibir(Message mensaje) {
        MensajeReevaluacion pedido = leer(new String(mensaje.getBody(), StandardCharsets.UTF_8));
        servicio.reevaluar(pedido.reclamoNumero(), pedido.intento());
    }

    MensajeReevaluacion leer(String texto) {
        MensajeReevaluacion pedido;
        try {
            pedido = json.readValue(texto, MensajeReevaluacion.class);
        } catch (IOException error) {
            throw new AmqpRejectAndDontRequeueException("El pedido de reevaluación no es JSON válido", error);
        }
        if (pedido == null || pedido.version() != MensajeReevaluacion.VERSION || pedido.reclamoNumero() == null
                || pedido.reclamoNumero().isBlank() || pedido.intento() < 1) {
            throw new AmqpRejectAndDontRequeueException("Pedido de reevaluación inválido: " + texto);
        }
        return pedido;
    }
}
