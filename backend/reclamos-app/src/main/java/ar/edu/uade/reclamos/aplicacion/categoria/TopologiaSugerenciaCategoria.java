package ar.edu.uade.reclamos.aplicacion.categoria;

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
 * Colas de la sugerencia de categoría (PR 26). Siguen el mismo esquema que las de la reevaluación de prioridad
 * (PR 24): una cola con consumidor, una de espera sin consumidor para el reintento diferido y una de errores.
 *
 * <pre>
 * reclamos.eventos ──ia.categoria.sugerir──▶ reclamos.categoria ──error inesperado──▶ reclamos.categoria.dlq
 *        │                                          ▲
 *        └──ia.categoria.espera──▶ reclamos.categoria.espera (vencido el tiempo de vida) ┘
 * </pre>
 */
public final class TopologiaSugerenciaCategoria {

    public static final String COLA = "reclamos.categoria";
    public static final String COLA_ESPERA = "reclamos.categoria.espera";
    public static final String COLA_ERRORES = "reclamos.categoria.dlq";
    public static final String CLAVE = "ia.categoria.sugerir";
    public static final String CLAVE_ESPERA = "ia.categoria.espera";

    private TopologiaSugerenciaCategoria() {
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
