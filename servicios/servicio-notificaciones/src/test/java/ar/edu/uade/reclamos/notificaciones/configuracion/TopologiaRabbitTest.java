package ar.edu.uade.reclamos.notificaciones.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

/** La topología coincide con docs/contratos/mensajes.md. */
class TopologiaRabbitTest {

    private final Declarables topologia = new TopologiaRabbit().topologiaAvisos();

    @Test
    void laColaDeAvisosEsDurableYMandaLosRechazadosAlExchangeDeErrores() {
        Queue cola = cola("notificaciones.email");

        assertThat(cola.isDurable()).isTrue();
        assertThat(cola.getArguments()).containsEntry("x-dead-letter-exchange", "reclamos.eventos.dlx");
        assertThat(cola("notificaciones.email.dlq").isDurable()).isTrue();
    }

    @Test
    void losExchangesSonDurablesYDelTipoDelContrato() {
        List<Exchange> exchanges = topologia.getDeclarablesByType(Exchange.class);

        assertThat(exchanges).extracting(Exchange::getName, Exchange::getType, Exchange::isDurable)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("reclamos.eventos", "topic", true),
                        org.assertj.core.groups.Tuple.tuple("reclamos.eventos.dlx", "fanout", true));
    }

    @Test
    void ligaLaColaConAvisoNumeralYLaDeErroresAlFanout() {
        assertThat(topologia.getDeclarablesByType(Binding.class))
                .extracting(Binding::getDestination, Binding::getExchange, Binding::getRoutingKey)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("notificaciones.email", "reclamos.eventos", "aviso.#"),
                        org.assertj.core.groups.Tuple.tuple("notificaciones.email.dlq", "reclamos.eventos.dlx", ""));
    }

    private Queue cola(String nombre) {
        return topologia.getDeclarablesByType(Queue.class).stream()
                .filter(cola -> cola.getName().equals(nombre)).findFirst().orElseThrow();
    }
}
