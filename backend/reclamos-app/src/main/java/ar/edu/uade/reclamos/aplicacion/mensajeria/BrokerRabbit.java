package ar.edu.uade.reclamos.aplicacion.mensajeria;

import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Publica en el exchange {@code reclamos.eventos} de RabbitMQ con confirmación del broker
 * ({@code publisher-confirm-type: correlated}): un mensaje cuenta como publicado recién cuando RabbitMQ lo
 * confirma. Mensaje persistente, en JSON, con el {@code eventId} como {@code message-id}.
 */
public class BrokerRabbit implements BrokerMensajes {

    public static final String EXCHANGE = "reclamos.eventos";

    private final RabbitTemplate rabbit;
    private final Duration esperaConfirmacion;

    public BrokerRabbit(RabbitTemplate rabbit, Duration esperaConfirmacion) {
        this.rabbit = rabbit;
        this.esperaConfirmacion = esperaConfirmacion;
    }

    @Override
    public void publicar(MensajeSaliente mensaje) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        propiedades.setMessageId(mensaje.getEventId());
        propiedades.setType(mensaje.getTipoEvento());
        Message amqp = new Message(mensaje.getContenido().getBytes(StandardCharsets.UTF_8), propiedades);

        CorrelationData correlacion = new CorrelationData(mensaje.getEventId());
        rabbit.send(EXCHANGE, mensaje.getClaveRuteo(), amqp, correlacion);
        CorrelationData.Confirm confirmacion;
        try {
            confirmacion = correlacion.getFuture().get(esperaConfirmacion.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Se interrumpió la espera de la confirmación de RabbitMQ", interrumpido);
        } catch (ExecutionException | TimeoutException sinConfirmar) {
            throw new AmqpException("RabbitMQ no confirmó el mensaje " + mensaje.getEventId(), sinConfirmar);
        }
        if (!confirmacion.isAck()) {
            throw new AmqpException("RabbitMQ rechazó el mensaje " + mensaje.getEventId() + ": "
                    + confirmacion.getReason());
        }
    }
}
