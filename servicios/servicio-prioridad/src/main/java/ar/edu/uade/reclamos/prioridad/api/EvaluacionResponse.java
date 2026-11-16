package ar.edu.uade.reclamos.prioridad.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import ar.edu.uade.reclamos.prioridad.evaluacion.Evaluacion;
import ar.edu.uade.reclamos.prioridad.evaluacion.Prioridad;
import io.swagger.v3.oas.annotations.media.Schema;

/** Los cuatro campos vienen siempre: el contrato escrito (docs/contratos/rest2-prioridad.yaml) los exige. */
public record EvaluacionResponse(
        @Schema(example = "CRITICA", requiredMode = REQUIRED) Prioridad prioridad,
        @Schema(example = "Hay cables expuestos en un lugar con chicos: riesgo eléctrico inmediato.", maxLength = 300,
                requiredMode = REQUIRED)
        String motivo,
        @Schema(example = "gemini", allowableValues = {"gemini", "openai", "simulado"}, requiredMode = REQUIRED)
        String proveedor,
        @Schema(example = "gemini-3.5-flash-lite", requiredMode = REQUIRED) String modelo) {

    static EvaluacionResponse de(Evaluacion evaluacion) {
        return new EvaluacionResponse(evaluacion.prioridad(), evaluacion.motivo(), evaluacion.proveedor(),
                evaluacion.modelo());
    }
}
