package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * PR 27: los posibles duplicados del alta. Si la IA eligió uno, es el primero y tiene {@code masParecido: true}.
 *
 * @param comparadoPorIA {@code false} sin IA o si no respondió: los candidatos son los de la misma categoría
 */
public record DuplicadosDelAltaResponse(
        boolean comparadoPorIA,
        @Schema(description = "Explicación de la IA; null si no comparó", nullable = true) String motivo,
        List<CandidatoDuplicadoResponse> candidatos) { }
