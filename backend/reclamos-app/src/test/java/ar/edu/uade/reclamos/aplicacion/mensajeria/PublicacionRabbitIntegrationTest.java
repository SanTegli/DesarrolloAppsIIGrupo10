package ar.edu.uade.reclamos.aplicacion.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Publicación contra un RabbitMQ de verdad. Corre solo si está definida {@code RABBITMQ_HOST}: en la
 * integración continua hay un RabbitMQ como servicio; en una máquina sin broker el test se saltea.
 */
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:rabbit;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.mensajeria.habilitada=true", "reclamos.mensajeria.intervalo-ms=3600000"})
@ActiveProfiles("test")
class PublicacionRabbitIntegrationTest {

    @Autowired AmqpAdmin admin;
    @Autowired RabbitTemplate rabbit;
    @Autowired MensajeSalienteRepository mensajes;
    @Autowired PublicadorMensajes publicador;

    @Test
    void publicaConConfirmacionYElMensajeLlegaConSuClaveYSuEventId() {
        TopicExchange exchange = new TopicExchange(BrokerRabbit.EXCHANGE, true, false);
        admin.declareExchange(exchange);
        Queue cola = admin.declareQueue();
        admin.declareBinding(BindingBuilder.bind(cola).to(exchange).with("aviso.#"));
        String eventId = UUID.randomUUID().toString();
        String contenido = "{\"eventId\":\"" + eventId + "\",\"version\":1}";
        mensajes.guardar(new MensajeSaliente(eventId, "aviso.reclamo-creado", "ReclamoCreado", contenido,
                LocalDateTime.now()));

        assertThat(publicador.publicarPendientes()).isEqualTo(1);

        Message recibido = rabbit.receive(cola.getName(), 10_000);
        assertThat(recibido).isNotNull();
        assertThat(new String(recibido.getBody(), StandardCharsets.UTF_8)).isEqualTo(contenido);
        assertThat(recibido.getMessageProperties().getMessageId()).isEqualTo(eventId);
        assertThat(recibido.getMessageProperties().getReceivedRoutingKey()).isEqualTo("aviso.reclamo-creado");
        assertThat(recibido.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(recibido.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(mensajes.buscarPorEventId(eventId).orElseThrow().getEstado()).isEqualTo(EstadoMensaje.PUBLICADO);
    }
}
