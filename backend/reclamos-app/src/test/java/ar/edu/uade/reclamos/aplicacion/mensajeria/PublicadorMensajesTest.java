package ar.edu.uade.reclamos.aplicacion.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;

class PublicadorMensajesTest {

    private static final LocalDateTime CREADO = LocalDateTime.of(2026, 11, 2, 18, 0);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-02T21:00:05Z"), ZoneOffset.UTC);
    private final OutboxEnMemoria outbox = new OutboxEnMemoria();
    private final List<String> publicados = new ArrayList<>();
    private String caeEn;

    private final BrokerMensajes broker = mensaje -> {
        if (mensaje.getEventId().equals(caeEn)) {
            throw new AmqpConnectException(new java.net.ConnectException("Connection refused"));
        }
        publicados.add(mensaje.getEventId());
    };

    @Test
    void publicaLosPendientesEnOrdenYLosMarcaPublicados() {
        pendientes("e1", "e2", "e3");

        int cantidad = new PublicadorMensajes(outbox, broker, reloj, 50).publicarPendientes();

        assertThat(cantidad).isEqualTo(3);
        assertThat(publicados).containsExactly("e1", "e2", "e3");
        assertThat(outbox.contarPorEstado(EstadoMensaje.PENDIENTE)).isZero();
        assertThat(outbox.guardados).allSatisfy(mensaje ->
                assertThat(mensaje.getFechaPublicacion()).isEqualTo(LocalDateTime.of(2026, 11, 2, 21, 0, 5)));
    }

    @Test
    void publicaComoMaximoElLote() {
        pendientes("e1", "e2", "e3");

        assertThat(new PublicadorMensajes(outbox, broker, reloj, 2).publicarPendientes()).isEqualTo(2);
        assertThat(outbox.contarPorEstado(EstadoMensaje.PENDIENTE)).isEqualTo(1);
    }

    @Test
    void siElBrokerFallaElMensajeQuedaPendienteConElErrorYSeCortaLaPasada() {
        pendientes("e1", "e2", "e3");
        caeEn = "e2";
        PublicadorMensajes publicador = new PublicadorMensajes(outbox, broker, reloj, 50);

        assertThat(publicador.publicarPendientes()).isEqualTo(1);
        MensajeSaliente fallido = outbox.buscarPorEventId("e2").orElseThrow();
        assertThat(fallido.isPendiente()).isTrue();
        assertThat(fallido.getIntentos()).isEqualTo(1);
        assertThat(fallido.getUltimoError()).contains("Connection refused");
        assertThat(publicados).containsExactly("e1");

        caeEn = null;
        assertThat(publicador.publicarPendientes()).isEqualTo(2);
        assertThat(publicados).containsExactly("e1", "e2", "e3");
        assertThat(outbox.contarPorEstado(EstadoMensaje.PENDIENTE)).isZero();
    }

    @Test
    void sinPendientesNoHaceNada() {
        assertThat(new PublicadorMensajes(outbox, broker, reloj, 50).publicarPendientes()).isZero();
        assertThat(outbox.guardadas).isZero();
    }

    private void pendientes(String... eventIds) {
        for (String eventId : eventIds) {
            outbox.guardar(new MensajeSaliente(eventId, "aviso.reclamo-creado", "ReclamoCreado", "{}", CREADO));
        }
        outbox.guardadas = 0;
    }
}
