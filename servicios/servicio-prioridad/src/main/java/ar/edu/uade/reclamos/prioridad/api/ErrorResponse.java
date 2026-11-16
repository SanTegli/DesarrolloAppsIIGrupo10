package ar.edu.uade.reclamos.prioridad.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El mismo cuerpo de error que REST 1, para que el backend y Swagger los lean igual. Todos los campos vienen
 * siempre; {@code detalles} puede ser una lista vacía.
 */
public record ErrorResponse(
        @Schema(example = "504", requiredMode = REQUIRED) int status,
        @Schema(example = "TIEMPO_AGOTADO", requiredMode = REQUIRED) String codigo,
        @Schema(example = "Gemini no respondió a tiempo", requiredMode = REQUIRED) String mensaje,
        @Schema(requiredMode = REQUIRED) List<String> detalles,
        @Schema(example = "/api/prioridades/evaluar", requiredMode = REQUIRED) String ruta,
        @Schema(requiredMode = REQUIRED) LocalDateTime fecha) { }
