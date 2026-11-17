package ar.edu.uade.reclamos.prioridad.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import ar.edu.uade.reclamos.prioridad.evaluacion.ComparacionDuplicados;
import io.swagger.v3.oas.annotations.media.Schema;

/** {@code similar} es el id de uno de los candidatos, o {@code null} si ninguno es el mismo problema. */
public record ComparacionDuplicadosResponse(
        @Schema(example = "1", nullable = true, description = "null si ningún candidato es el mismo problema")
        String similar,
        @Schema(example = "Los dos hablan del mismo farol apagado en la esquina.", maxLength = 300,
                requiredMode = REQUIRED)
        String motivo,
        @Schema(example = "gemini", allowableValues = {"gemini", "openai", "simulado"}, requiredMode = REQUIRED)
        String proveedor,
        @Schema(example = "gemini-3.5-flash-lite", requiredMode = REQUIRED) String modelo) {

    static ComparacionDuplicadosResponse de(ComparacionDuplicados comparacion) {
        return new ComparacionDuplicadosResponse(comparacion.similar(), comparacion.motivo(), comparacion.proveedor(),
                comparacion.modelo());
    }
}
