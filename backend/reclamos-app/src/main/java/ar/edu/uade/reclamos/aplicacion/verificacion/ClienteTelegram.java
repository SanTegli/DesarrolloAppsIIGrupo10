package ar.edu.uade.reclamos.aplicacion.verificacion;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente de la Bot API de Telegram (Adapter y capa anticorrupción): de las respuestas de Telegram se queda
 * solo con lo que usa la verificación. Usa {@code getUpdates}, así no hace falta webhook ni URL pública.
 *
 * <p>El token va en la ruta ({@code /bot<token>/...}) y los errores de {@code RestClient} incluyen la URL. Por
 * eso ninguna excepción de acá conserva la causa original: el token no puede terminar en un log.
 */
public class ClienteTelegram {

    /** Qué teclado acompaña al mensaje. */
    public enum Teclado { NINGUNO, PEDIR_CONTACTO, QUITAR }

    public record Contacto(String telefono, Long usuarioId) { }

    /** Un mensaje que recibió el bot. {@code contacto} no es nulo cuando el ciudadano compartió su número. */
    public record Mensaje(long idActualizacion, long chatId, Long remitenteId, String texto, Contacto contacto) { }

    private final RestClient http;
    private final String token;

    public ClienteTelegram(RestClient http, String token) {
        this.http = http;
        this.token = token;
    }

    public static ClienteTelegram crear(String url, String token, Duration tiempoLimite) {
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout((int) tiempoLimite.toMillis());
        tiempos.setReadTimeout((int) tiempoLimite.toMillis());
        return new ClienteTelegram(RestClient.builder().baseUrl(url).requestFactory(tiempos).build(), token);
    }

    /** Los mensajes con {@code update_id >= desde}. Pedir con {@code desde} confirma los anteriores. */
    public List<Mensaje> recibir(long desde) {
        JsonNode respuesta;
        try {
            respuesta = http.get()
                    .uri("/bot" + token + "/getUpdates?offset={desde}&timeout=0&allowed_updates={tipos}", desde,
                            "[\"message\"]")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException error) {
            throw new TelegramNoDisponibleException("Telegram no respondió a getUpdates");
        }
        if (respuesta == null || !respuesta.path("ok").asBoolean(false)) {
            throw new TelegramNoDisponibleException("Telegram respondió getUpdates con error");
        }
        List<Mensaje> mensajes = new ArrayList<>();
        for (JsonNode actualizacion : respuesta.path("result")) {
            JsonNode mensaje = actualizacion.path("message");
            long id = actualizacion.path("update_id").asLong();
            if (mensaje.isMissingNode() || !mensaje.path("chat").has("id")) {
                // Otro tipo de novedad (por ejemplo, un mensaje editado): se confirma sin procesar.
                mensajes.add(new Mensaje(id, 0, null, null, null));
                continue;
            }
            JsonNode contacto = mensaje.path("contact");
            mensajes.add(new Mensaje(id,
                    mensaje.path("chat").path("id").asLong(),
                    mensaje.path("from").has("id") ? mensaje.path("from").path("id").asLong() : null,
                    mensaje.hasNonNull("text") ? mensaje.get("text").asText() : null,
                    contacto.isMissingNode() ? null : new Contacto(contacto.path("phone_number").asText(null),
                            contacto.has("user_id") ? contacto.path("user_id").asLong() : null)));
        }
        return mensajes;
    }

    public void enviar(long chatId, String texto, Teclado teclado) {
        Map<String, Object> pedido = switch (teclado) {
            case NINGUNO -> Map.of("chat_id", chatId, "text", texto);
            case QUITAR -> Map.of("chat_id", chatId, "text", texto, "reply_markup", Map.of("remove_keyboard", true));
            case PEDIR_CONTACTO -> Map.of("chat_id", chatId, "text", texto, "reply_markup", Map.of(
                    "keyboard", List.of(List.of(Map.of("text", "Compartir mi número", "request_contact", true))),
                    "one_time_keyboard", true,
                    "resize_keyboard", true));
        };
        try {
            http.post().uri("/bot" + token + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pedido)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException error) {
            throw new TelegramNoDisponibleException("Telegram no aceptó sendMessage");
        }
    }
}
