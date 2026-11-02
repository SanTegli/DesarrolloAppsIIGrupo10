package ar.edu.uade.reclamos.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.notificaciones.configuracion.TopologiaRabbit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * El servicio completo contra RabbitMQ y Mailpit de verdad: un aviso publicado en el exchange termina como
 * correo en el buzón de Mailpit, un repetido no manda otro correo y uno inválido termina en la cola de errores.
 *
 * <p>Corre solo si están definidas {@code RABBITMQ_HOST} y {@code MAILPIT_URL} (la integración continua las
 * define y levanta los dos como servicios). Sin ellas, se saltea.
 */
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MAILPIT_URL", matches = ".+")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flujo;DB_CLOSE_DELAY=-1")
class FlujoCorreoIntegrationTest {

    private static final String MAILPIT = System.getenv("MAILPIT_URL");
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Autowired RabbitTemplate rabbit;

    @Test
    void unAvisoPublicadoLlegaComoCorreoYElRepetidoNoSeManda() throws Exception {
        String reclamo = "REC-" + String.format(Locale.ROOT, "%08X", new Random().nextInt() & 0x7fffffff);
        String eventId = UUID.randomUUID().toString();
        String aviso = """
                {"eventId": "%s", "version": 1, "tipoEvento": "ReclamoCreado", "reclamoNumero": "%s",
                 "destinatario": {"id": 1, "nombre": "Ana Pérez", "email": "ana@example.org"},
                 "mensaje": "Tu reclamo %s fue ingresado.", "fechaEvento": "2026-11-02T18:00:00-03:00"}"""
                .formatted(eventId, reclamo, reclamo);

        publicar(aviso, "aviso.reclamo-creado");
        esperar(() -> correosDe(reclamo) == 1, Duration.ofSeconds(20));

        publicar(aviso, "aviso.reclamo-creado");
        Thread.sleep(3_000);
        assertThat(correosDe(reclamo)).as("el repetido no manda otro correo").isEqualTo(1);
    }

    @Test
    void unMensajeInvalidoTerminaEnLaColaDeErroresSinReintentos() throws Exception {
        String invalido = "{\"version\": 99, \"marca\": \"" + UUID.randomUUID() + "\"}";

        publicar(invalido, "aviso.reclamo-creado");

        Message enErrores = null;
        Instant limite = Instant.now().plusSeconds(20);
        while (enErrores == null && Instant.now().isBefore(limite)) {
            Message recibido = rabbit.receive(TopologiaRabbit.COLA_ERRORES, 1_000);
            if (recibido != null && new String(recibido.getBody(), StandardCharsets.UTF_8).equals(invalido)) {
                enErrores = recibido;
            }
        }
        assertThat(enErrores).as("el mensaje inválido en " + TopologiaRabbit.COLA_ERRORES).isNotNull();
    }

    private void publicar(String cuerpo, String clave) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(TopologiaRabbit.EXCHANGE, clave, new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades));
    }

    /** Cuántos correos hay en Mailpit con el número de reclamo en el asunto. */
    private int correosDe(String reclamo) throws Exception {
        HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(
                URI.create(MAILPIT.replaceAll("/$", "") + "/api/v1/messages?limit=500")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int cantidad = 0;
        for (JsonNode mensaje : json.readTree(respuesta.body()).path("messages")) {
            if (mensaje.path("Subject").asText().contains(reclamo)) {
                cantidad++;
            }
        }
        return cantidad;
    }

    private static void esperar(BooleanSupplierConError condicion, Duration maximo) throws Exception {
        Instant limite = Instant.now().plus(maximo);
        while (!condicion.cumple()) {
            if (Instant.now().isAfter(limite)) {
                throw new AssertionError("No se cumplió la condición en " + maximo.toSeconds() + " segundos");
            }
            Thread.sleep(500);
        }
    }

    @FunctionalInterface
    private interface BooleanSupplierConError {
        boolean cumple() throws Exception;
    }
}
