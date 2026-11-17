package ar.edu.uade.reclamos.prioridad.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import ar.edu.uade.reclamos.prioridad.evaluacion.SugerenciaCategoria;
import io.swagger.v3.oas.annotations.media.Schema;

/** {@code categoria} es una de las recibidas, o {@code null} si ninguna corresponde. */
public record SugerenciaCategoriaResponse(
        @Schema(example = "Vereda rota", nullable = true, description = "null si ninguna categoría corresponde")
        String categoria,
        @Schema(example = "Las baldosas sueltas son un problema de la vereda.", maxLength = 300, requiredMode = REQUIRED)
        String motivo,
        @Schema(example = "gemini", allowableValues = {"gemini", "openai", "simulado"}, requiredMode = REQUIRED)
        String proveedor,
        @Schema(example = "gemini-3.5-flash-lite", requiredMode = REQUIRED) String modelo) {

    static SugerenciaCategoriaResponse de(SugerenciaCategoria sugerencia) {
        return new SugerenciaCategoriaResponse(sugerencia.categoria(), sugerencia.motivo(), sugerencia.proveedor(),
                sugerencia.modelo());
    }
}
