package ar.edu.uade.reclamos.prioridad.proveedor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adapter para la API de Gemini ({@code models.generateContent}). Traduce la {@link ConsultaIA} al formato
 * de Google: instrucciones como {@code systemInstruction}, el reclamo como contenido del usuario, y un
 * {@code responseSchema} que obliga al modelo a contestar el JSON con la prioridad dentro del enum.
 *
 * <p>Autentica con el encabezado {@code x-goog-api-key}; la clave sale de {@code GEMINI_API_KEY}. En el nivel
 * gratuito Google puede usar el contenido para mejorar sus productos: mandar solo datos de prueba.
 */
public class GeminiAdapter implements ProveedorIA {

    static final String RUTA = "/v1beta/models/{modelo}:generateContent";

    /** Esquema de Gemini (subconjunto de OpenAPI, tipos en mayúscula). */
    static final Map<String, Object> ESQUEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    EsquemaRespuesta.CAMPO_PRIORIDAD, Map.of("type", "STRING", "enum", EsquemaRespuesta.PRIORIDADES),
                    EsquemaRespuesta.CAMPO_MOTIVO, Map.of("type", "STRING")),
            "required", List.of(EsquemaRespuesta.CAMPO_PRIORIDAD, EsquemaRespuesta.CAMPO_MOTIVO),
            "propertyOrdering", List.of(EsquemaRespuesta.CAMPO_PRIORIDAD, EsquemaRespuesta.CAMPO_MOTIVO));

    private final RestClient http;
    private final String clave;
    private final String modelo;
    private final ObjectMapper json;

    public GeminiAdapter(RestClient http, String clave, String modelo, ObjectMapper json) {
        this.http = http;
        this.clave = clave;
        this.modelo = modelo;
        this.json = json;
    }

    @Override
    public String nombre() {
        return "gemini";
    }

    @Override
    public String modelo() {
        return modelo;
    }

    @Override
    public RespuestaIA sugerir(ConsultaIA consulta) {
        if (clave == null || clave.isBlank()) {
            throw new ProveedorNoDisponibleException("Falta la clave de Gemini: definir GEMINI_API_KEY");
        }
        Map<String, Object> pedido = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", consulta.instrucciones()))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", consulta.reclamo())))),
                "generationConfig", Map.of("responseMimeType", "application/json", "responseSchema", ESQUEMA));
        JsonNode respuesta;
        try {
            respuesta = http.post().uri(RUTA, modelo)
                    .header("x-goog-api-key", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pedido)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException error) {
            throw ErroresProveedor.traducir("Gemini", error);
        }
        return LectorRespuesta.leer(json, "Gemini", texto(respuesta));
    }

    /** Texto de la primera respuesta candidata, sin las partes de razonamiento interno. */
    private static String texto(JsonNode respuesta) {
        if (respuesta == null) {
            return null;
        }
        JsonNode candidata = respuesta.path("candidates").path(0);
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : candidata.path("content").path("parts")) {
            if (!parte.path("thought").asBoolean(false) && parte.hasNonNull("text")) {
                texto.append(parte.get("text").asText());
            }
        }
        if (texto.isEmpty() && candidata.hasNonNull("finishReason")) {
            throw new RespuestaInvalidaException("Gemini no devolvió texto (finishReason "
                    + candidata.get("finishReason").asText() + ")");
        }
        return texto.toString();
    }
}
