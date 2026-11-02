package ar.edu.uade.reclamos.aplicacion.configuracion;

import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerMensajes;
import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerRabbit;
import ar.edu.uade.reclamos.aplicacion.mensajeria.OutboxAvisos;
import ar.edu.uade.reclamos.aplicacion.mensajeria.PublicadorMensajes;
import ar.edu.uade.reclamos.aplicacion.mensajeria.SalidaDeAvisos;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mensajería con RabbitMQ, encendida con {@code reclamos.mensajeria.habilitada: true}.
 *
 * <ul>
 *   <li>Apagada: los avisos quedan solo como avisos internos ({@link SalidaDeAvisos#NINGUNA}).</li>
 *   <li>Encendida: cada aviso va a la outbox ({@link OutboxAvisos}) y {@link PublicadorMensajes} los publica
 *       en el exchange {@code reclamos.eventos}, que declara el backend porque es el productor.</li>
 * </ul>
 *
 * <p>Las colas, la cola de errores y los reintentos son del consumidor ({@code servicio-notificaciones}).
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionMensajeria {

    @Bean
    public SalidaDeAvisos salidaDeAvisos(PropiedadesReclamos propiedades,
                                         ObjectProvider<MensajeSalienteRepository> mensajes,
                                         ObjectProvider<ObjectMapper> json, Clock reloj) {
        if (!propiedades.mensajeria().habilitada()) {
            return SalidaDeAvisos.NINGUNA;
        }
        return new OutboxAvisos(mensajes.getObject(), json.getIfAvailable(ObjectMapper::new), reloj);
    }

    /** {@code @EnableScheduling} acá también: la tarea de vencimientos, que lo declara, se puede apagar. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "reclamos.mensajeria", name = "habilitada", havingValue = "true")
    static class ConRabbit {

        @Bean
        public TopicExchange exchangeReclamos() {
            return ExchangeBuilder.topicExchange(BrokerRabbit.EXCHANGE).durable(true).build();
        }

        @Bean
        public BrokerMensajes brokerMensajes(RabbitTemplate rabbit, PropiedadesReclamos propiedades) {
            return new BrokerRabbit(rabbit, propiedades.mensajeria().esperaConfirmacion());
        }

        @Bean
        public PublicadorMensajes publicadorMensajes(MensajeSalienteRepository mensajes, BrokerMensajes broker,
                                                     Clock reloj, PropiedadesReclamos propiedades) {
            return new PublicadorMensajes(mensajes, broker, reloj, propiedades.mensajeria().lote());
        }
    }
}
