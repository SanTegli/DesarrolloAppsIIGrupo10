package ar.edu.uade.reclamos.aplicacion.configuracion;

import ar.edu.uade.reclamos.aplicacion.integracion.ClienteServicioPrioridad;
import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerMensajes;
import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerRabbit;
import ar.edu.uade.reclamos.aplicacion.mensajeria.OutboxAvisos;
import ar.edu.uade.reclamos.aplicacion.mensajeria.PublicadorMensajes;
import ar.edu.uade.reclamos.aplicacion.mensajeria.SalidaDeAvisos;
import ar.edu.uade.reclamos.aplicacion.prioridad.ListenerReevaluacionPrioridad;
import ar.edu.uade.reclamos.aplicacion.prioridad.OutboxReevaluaciones;
import ar.edu.uade.reclamos.aplicacion.prioridad.ServicioReevaluacionPrioridad;
import ar.edu.uade.reclamos.aplicacion.prioridad.SolicitudesReevaluacion;
import ar.edu.uade.reclamos.aplicacion.prioridad.TopologiaReevaluacion;
import ar.edu.uade.reclamos.dominio.evento.PublicadorEventos;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
 * <p>Las colas, la cola de errores y los reintentos de los avisos son del consumidor
 * ({@code servicio-notificaciones}). Las de la reevaluación de prioridad son del backend, que en ese caso es
 * productor y consumidor a la vez ({@link TopologiaReevaluacion}).
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

    /**
     * Los pedidos de reevaluación de prioridad. Sin la estrategia {@code ia} o sin mensajería no hay segunda
     * oportunidad ({@link SolicitudesReevaluacion#NINGUNA}): si la IA no responde en el alta, queda el respaldo.
     */
    @Bean
    public SolicitudesReevaluacion solicitudesReevaluacion(PropiedadesReclamos propiedades,
                                                           ObjectProvider<MensajeSalienteRepository> mensajes,
                                                           ObjectProvider<ObjectMapper> json, Clock reloj) {
        if (!propiedades.reevaluaPrioridadConIA()) {
            return SolicitudesReevaluacion.NINGUNA;
        }
        return new OutboxReevaluaciones(mensajes.getObject(), json.getIfAvailable(ObjectMapper::new), reloj);
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

    /**
     * Reevaluación de la prioridad por la cola: solo con la mensajería encendida, la estrategia {@code ia} y
     * {@code reclamos.prioridad.ia.reevaluacion.habilitada} (la misma condición que
     * {@link PropiedadesReclamos#reevaluaPrioridadConIA()}).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression("${reclamos.mensajeria.habilitada:false} "
            + "and '${reclamos.prioridad.estrategia:categoria}' == 'ia' "
            + "and ${reclamos.prioridad.ia.reevaluacion.habilitada:true}")
    static class ConReevaluacion {

        @Bean
        public Declarables topologiaReevaluacion(PropiedadesReclamos propiedades) {
            return TopologiaReevaluacion.declarar(propiedades.prioridad().ia().reevaluacion().espera());
        }

        /**
         * Con su propio cliente de REST 2: espera más que el del alta (nadie está esperando) y tiene su propio
         * circuito, para que las fallas de un lado no corten el otro.
         */
        @Bean
        public ServicioReevaluacionPrioridad servicioReevaluacionPrioridad(PropiedadesReclamos propiedades,
                                                                           ReclamoRepository reclamos,
                                                                           SolicitudesReevaluacion solicitudes,
                                                                           PublicadorEventos eventos, Clock reloj) {
            PropiedadesReclamos.Ia ia = propiedades.prioridad().ia();
            ClienteServicioPrioridad cliente = ClienteServicioPrioridad.crear(ia.url(), ia.tiempoConexion(),
                    ia.reevaluacion().tiempoRespuesta(), ia.circuito().llamadas(), ia.circuito().espera());
            return new ServicioReevaluacionPrioridad(reclamos, cliente, solicitudes, eventos, reloj,
                    ia.reevaluacion().intentos());
        }

        @Bean
        public ListenerReevaluacionPrioridad listenerReevaluacionPrioridad(ServicioReevaluacionPrioridad servicio,
                                                                           ObjectProvider<ObjectMapper> json) {
            return new ListenerReevaluacionPrioridad(servicio, json.getIfAvailable(ObjectMapper::new));
        }
    }
}
