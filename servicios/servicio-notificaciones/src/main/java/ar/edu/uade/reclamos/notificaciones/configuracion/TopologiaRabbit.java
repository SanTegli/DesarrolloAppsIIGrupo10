package ar.edu.uade.reclamos.notificaciones.configuracion;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchanges y colas del contrato ({@code docs/contratos/mensajes.md}). Spring los declara en RabbitMQ al
 * conectarse; si ya existen con la misma definición, no cambia nada.
 *
 * <pre>
 * reclamos.eventos (topic) ──aviso.#──▶ notificaciones.email ──rechazado──▶ reclamos.eventos.dlx (fanout)
 *                                                                              └──▶ notificaciones.email.dlq
 * </pre>
 *
 * <p>El backend también declara {@code reclamos.eventos}, igual que acá: así cualquiera de los dos puede
 * arrancar primero.
 */
@Configuration(proxyBeanMethods = false)
public class TopologiaRabbit {

    public static final String EXCHANGE = "reclamos.eventos";
    public static final String COLA = "notificaciones.email";
    public static final String CLAVE = "aviso.#";
    public static final String EXCHANGE_ERRORES = "reclamos.eventos.dlx";
    public static final String COLA_ERRORES = "notificaciones.email.dlq";

    @Bean
    public Declarables topologiaAvisos() {
        TopicExchange eventos = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        FanoutExchange errores = ExchangeBuilder.fanoutExchange(EXCHANGE_ERRORES).durable(true).build();
        Queue cola = QueueBuilder.durable(COLA).deadLetterExchange(EXCHANGE_ERRORES).build();
        Queue colaErrores = QueueBuilder.durable(COLA_ERRORES).build();
        Binding avisos = BindingBuilder.bind(cola).to(eventos).with(CLAVE);
        Binding rechazados = BindingBuilder.bind(colaErrores).to(errores);
        return new Declarables(eventos, errores, cola, colaErrores, avisos, rechazados);
    }
}
