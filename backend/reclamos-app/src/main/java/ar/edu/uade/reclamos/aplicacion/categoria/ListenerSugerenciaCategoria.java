package ar.edu.uade.reclamos.aplicacion.categoria;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Consumidor de {@code reclamos.categoria}. Un mensaje ilegible o de otra versión va directo a
 * {@code reclamos.categoria.dlq}.
 */
public class ListenerSugerenciaCategoria {

    private final ServicioSugerenciaCategoria servicio;
    private final ObjectMapper json;

    public ListenerSugerenciaCategoria(ServicioSugerenciaCategoria servicio, ObjectMapper json) {
        this.servicio = servicio;
        this.json = json;
    }

    @RabbitListener(queues = TopologiaSugerenciaCategoria.COLA)
    public void recibir(Message mensaje) {
        MensajeSugerenciaCategoria pedido = leer(new String(mensaje.getBody(), StandardCharsets.UTF_8));
        servicio.sugerir(pedido.reclamoNumero(), pedido.intento());
    }

    MensajeSugerenciaCategoria leer(String texto) {
        MensajeSugerenciaCategoria pedido;
        try {
            pedido = json.readValue(texto, MensajeSugerenciaCategoria.class);
        } catch (IOException error) {
            throw new AmqpRejectAndDontRequeueException("El pedido de sugerencia de categoría no es JSON válido",
                    error);
        }
        if (pedido == null || pedido.version() != MensajeSugerenciaCategoria.VERSION
                || pedido.reclamoNumero() == null || pedido.reclamoNumero().isBlank() || pedido.intento() < 1) {
            throw new AmqpRejectAndDontRequeueException("Pedido de sugerencia de categoría inválido: " + texto);
        }
        return pedido;
    }
}
