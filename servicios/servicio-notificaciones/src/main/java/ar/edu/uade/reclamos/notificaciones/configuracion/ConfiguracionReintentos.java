package ar.edu.uade.reclamos.notificaciones.configuracion;

import ar.edu.uade.reclamos.notificaciones.aviso.MensajeInvalidoException;
import java.util.Map;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Reintentos del consumidor. La cantidad y la espera creciente salen de
 * {@code spring.rabbitmq.listener.simple.retry} (3 intentos, esperando 1 y 2 segundos). Cuando se agotan, el
 * mensaje se rechaza sin volver a la cola ({@code default-requeue-rejected: false}) y RabbitMQ lo manda a la
 * cola de errores.
 *
 * <p>Acá se agrega una excepción: un {@link MensajeInvalidoException} no se reintenta, porque reintentarlo
 * no lo arregla. Va directo a la cola de errores.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionReintentos {

    @Bean
    public RabbitRetryTemplateCustomizer sinReintentosParaMensajesInvalidos(RabbitProperties rabbit) {
        int intentos = rabbit.getListener().getSimple().getRetry().getMaxAttempts();
        return (destino, reintentos) -> {
            if (destino == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                reintentos.setRetryPolicy(politica(intentos));
            }
        };
    }

    /** Reintenta cualquier falla hasta {@code intentos} veces, salvo un mensaje inválido (busca en las causas). */
    static SimpleRetryPolicy politica(int intentos) {
        return new SimpleRetryPolicy(intentos, Map.of(MensajeInvalidoException.class, false), true, true);
    }
}
