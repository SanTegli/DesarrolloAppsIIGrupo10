package ar.edu.uade.reclamos.aplicacion.prioridad;

import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerRabbit;
import java.time.Duration;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Colas de la reevaluación de prioridad. Son del backend, que es productor y consumidor a la vez; no se
 * mezclan con las de {@code servicio-notificaciones}.
 *
 * <pre>
 * reclamos.eventos ──ia.prioridad.reevaluar──▶ reclamos.prioridad ──error inesperado──▶ reclamos.prioridad.dlq
 *        │                                            ▲
 *        └──ia.prioridad.espera──▶ reclamos.prioridad.espera (vencido el tiempo de vida) ┘
 * </pre>
 *
 * <p>La cola de espera no tiene consumidor: el mensaje se queda ahí {@code espera} y, al vencer, RabbitMQ lo
 * devuelve a {@code reclamos.prioridad} por el exchange por defecto. Es un reintento diferido que no ocupa el
 * hilo del consumidor mientras espera.
 */
public final class TopologiaReevaluacion {

    public static final String COLA = "reclamos.prioridad";
    public static final String COLA_ESPERA = "reclamos.prioridad.espera";
    public static final String COLA_ERRORES = "reclamos.prioridad.dlq";
    public static final String CLAVE = "ia.prioridad.reevaluar";
    public static final String CLAVE_ESPERA = "ia.prioridad.espera";

    private TopologiaReevaluacion() {
    }

    public static Declarables declarar(Duration espera) {
        TopicExchange eventos = ExchangeBuilder.topicExchange(BrokerRabbit.EXCHANGE).durable(true).build();
        Queue cola = QueueBuilder.durable(COLA)
                .deadLetterExchange("")
                .deadLetterRoutingKey(COLA_ERRORES)
                .build();
        Queue colaEspera = QueueBuilder.durable(COLA_ESPERA)
                .ttl((int) espera.toMillis())
                .deadLetterExchange("")
                .deadLetterRoutingKey(COLA)
                .build();
        Queue colaErrores = QueueBuilder.durable(COLA_ERRORES).build();
        Binding pedidos = BindingBuilder.bind(cola).to(eventos).with(CLAVE);
        Binding esperas = BindingBuilder.bind(colaEspera).to(eventos).with(CLAVE_ESPERA);
        return new Declarables(cola, colaEspera, colaErrores, pedidos, esperas);
    }
}
