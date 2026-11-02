package ar.edu.uade.reclamos.prioridad.proveedor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lee el JSON que escribió el modelo ({@code {"prioridad": "...", "motivo": "..."}}). Es igual para todos los
 * proveedores: lo que cambia entre ellos es dónde viene ese texto dentro de la respuesta HTTP.
 */
final class LectorRespuesta {

    private LectorRespuesta() {
    }

    static RespuestaIA leer(ObjectMapper json, String proveedor, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RespuestaInvalidaException(proveedor + " no devolvió texto");
        }
        String limpio = texto.strip();
        // Algunos modelos envuelven el JSON en un bloque de código aunque se les pida JSON puro.
        if (limpio.startsWith("```")) {
            limpio = limpio.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        JsonNode objeto;
        try {
            objeto = json.readTree(limpio);
        } catch (JsonProcessingException error) {
            throw new RespuestaInvalidaException(proveedor + " no devolvió JSON", error);
        }
        if (objeto == null || !objeto.isObject()) {
            throw new RespuestaInvalidaException(proveedor + " no devolvió un objeto JSON");
        }
        return new RespuestaIA(texto(objeto, EsquemaRespuesta.CAMPO_PRIORIDAD),
                texto(objeto, EsquemaRespuesta.CAMPO_MOTIVO));
    }

    private static String texto(JsonNode objeto, String campo) {
        JsonNode valor = objeto.get(campo);
        return valor == null || !valor.isTextual() ? null : valor.asText();
    }
}
