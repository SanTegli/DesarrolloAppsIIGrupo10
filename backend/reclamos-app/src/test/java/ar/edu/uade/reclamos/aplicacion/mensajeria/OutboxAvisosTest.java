package ar.edu.uade.reclamos.aplicacion.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.dominio.modelo.CanalNotificacion;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El mensaje que queda en la outbox cumple el contrato de docs/contratos/mensajes.md. */
class OutboxAvisosTest {

    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final List<MensajeSaliente> guardados = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Clock buenosAires = Clock.fixed(Instant.parse("2026-11-02T21:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private final OutboxAvisos outbox = new OutboxAvisos(new OutboxEnMemoria(guardados), json, buenosAires);

    @Test
    void guardaUnMensajePendienteConLaClaveDeRuteoDelEvento() throws Exception {
        Notificacion aviso = aviso();

        outbox.enviar(aviso, TipoAviso.RECLAMO_ASIGNADO);

        assertThat(guardados).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.getEstado()).isEqualTo(EstadoMensaje.PENDIENTE);
            assertThat(mensaje.getClaveRuteo()).isEqualTo("aviso.reclamo-asignado");
            assertThat(mensaje.getTipoEvento()).isEqualTo("ReclamoAsignado");
            assertThat(mensaje.getEventId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(mensaje.getFechaCreacion()).isEqualTo(aviso.getFechaEnvio());
        });
    }

    @Test
    void elContenidoTieneLosCamposDelContratoYLaFechaConZonaHoraria() throws Exception {
        Notificacion aviso = aviso();

        outbox.enviar(aviso, TipoAviso.RECLAMO_CREADO);

        MensajeSaliente mensaje = guardados.get(0);
        JsonNode contenido = json.readTree(mensaje.getContenido());
        assertThat(contenido.get("eventId").asText()).isEqualTo(mensaje.getEventId());
        assertThat(contenido.get("version").asInt()).isEqualTo(1);
        assertThat(contenido.get("tipoEvento").asText()).isEqualTo("ReclamoCreado");
        assertThat(contenido.get("reclamoNumero").asText()).isEqualTo(aviso.getReclamo().getNumero());
        assertThat(contenido.at("/destinatario/nombre").asText()).isEqualTo("Ana Perez");
        assertThat(contenido.at("/destinatario/email").asText()).isEqualTo("ana@example.org");
        assertThat(contenido.get("mensaje").asText()).isEqualTo("Tu reclamo fue ingresado.");
        assertThat(contenido.get("fechaEvento").asText()).isEqualTo("2026-10-05T10:00:00-03:00");
    }

    @Test
    void nuncaLlevaDniTelefonoNiLaDescripcionDelReclamo() {
        Ciudadano conTelefono = new Ciudadano("30999888", "Luz", "Diaz", "luz@example.org", "1155550000");
        conTelefono.setId(9L);
        Reclamo reclamo = e.factory.crear(conTelefono, e.categoria, e.barrio, "Pozo frente a mi casa", "Belgrano 450");

        outbox.enviar(new Notificacion(reclamo, conTelefono, CanalNotificacion.INTERNO, "Aviso", LocalDateTime.now(e.reloj)),
                TipoAviso.RECLAMO_CREADO);

        assertThat(guardados.get(0).getContenido()).doesNotContain("30999888", "1155550000", "Pozo frente a mi casa",
                "Belgrano 450");
    }

    private Notificacion aviso() {
        return new Notificacion(e.ingresado(), e.ciudadano, CanalNotificacion.INTERNO, "Tu reclamo fue ingresado.",
                LocalDateTime.of(2026, 10, 5, 10, 0));
    }
}
