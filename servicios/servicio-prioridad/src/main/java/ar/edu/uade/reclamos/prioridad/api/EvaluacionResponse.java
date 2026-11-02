package ar.edu.uade.reclamos.prioridad.api;

import ar.edu.uade.reclamos.prioridad.evaluacion.Evaluacion;
import ar.edu.uade.reclamos.prioridad.evaluacion.Prioridad;
import io.swagger.v3.oas.annotations.media.Schema;

public record EvaluacionResponse(
        @Schema(example = "CRITICA") Prioridad prioridad,
        @Schema(example = "Hay cables expuestos en un lugar con chicos: riesgo eléctrico inmediato.", maxLength = 300)
        String motivo,
        @Schema(example = "gemini", allowableValues = {"gemini", "openai", "simulado"}) String proveedor,
        @Schema(example = "gemini-3.5-flash-lite") String modelo) {

    static EvaluacionResponse de(Evaluacion evaluacion) {
        return new EvaluacionResponse(evaluacion.prioridad(), evaluacion.motivo(), evaluacion.proveedor(),
                evaluacion.modelo());
    }
}
