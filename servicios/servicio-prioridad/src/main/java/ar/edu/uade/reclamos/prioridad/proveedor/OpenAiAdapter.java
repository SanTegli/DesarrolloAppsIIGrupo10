package ar.edu.uade.reclamos.prioridad.proveedor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter para la API de OpenAI ({@code chat/completions}). La misma {@link ConsultaIA} que usa Gemini va
 * como mensaje de sistema y de usuario, y la forma de la respuesta como {@code response_format} con un JSON
 * Schema estricto. Autentica con {@code Authorization: Bearer}; la clave sale de {@code OPENAI_API_KEY}.
 *
 * <p>Es el segundo proveedor: para cambiar de Gemini a OpenAI alcanza con
 * {@code prioridad.ia.proveedor: openai}, sin tocar el resto del servicio. No tiene nivel gratuito.
 */
public class OpenAiAdapter implements ProveedorIA {

    static final String RUTA = "/v1/chat/completions";

    /** JSON Schema estricto: en modo {@code strict} OpenAI exige {@code additionalProperties: false}. */
    static final Map<String, Object> ESQUEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    EsquemaRespuesta.CAMPO_PRIORIDAD, Map.of("type", "string", "enum", EsquemaRespuesta.PRIORIDADES),
                    EsquemaRespuesta.CAMPO_MOTIVO, Map.of("type", "string")),
            "required", List.of(EsquemaRespuesta.CAMPO_PRIORIDAD, EsquemaRespuesta.CAMPO_MOTIVO),
            "additionalProperties", false);

    private final RestClient http;
    private final String clave;
    private final String modelo;
    private final ObjectMapper json;

    public OpenAiAdapter(RestClient http, String clave, String modelo, ObjectMapper json) {
        this.http = http;
        this.clave = clave;
        this.modelo = modelo;
        this.json = json;
    }

    @Override
    public String nombre() {
        return "openai";
    }

    @Override
    public String modelo() {
        return modelo;
    }

    @Override
    public RespuestaIA sugerir(ConsultaIA consulta) {
        if (clave == null || clave.isBlank()) {
            throw new ProveedorNoDisponibleException("Falta la clave de OpenAI: definir OPENAI_API_KEY");
        }
        Map<String, Object> pedido = Map.of(
                "model", modelo,
                "messages", List.of(
                        Map.of("role", "system", "content", consulta.instrucciones()),
                        Map.of("role", "user", "content", consulta.reclamo())),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "prioridad_reclamo", "strict", true, "schema", ESQUEMA)));
        JsonNode respuesta;
        try {
            respuesta = http.post().uri(RUTA)
                    .header("Authorization", "Bearer " + clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pedido)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException error) {
            throw ErroresProveedor.traducir("OpenAI", error);
        }
        JsonNode mensaje = respuesta == null ? null : respuesta.path("choices").path(0).path("message");
        if (mensaje != null && mensaje.hasNonNull("refusal")) {
            throw new RespuestaInvalidaException("OpenAI se negó a responder");
        }
        String texto = mensaje == null || !mensaje.hasNonNull("content") ? null : mensaje.get("content").asText();
        return LectorRespuesta.leer(json, "OpenAI", texto);
    }
}
