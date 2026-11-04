package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Pendientes y vencidos de un área en un barrio. */
public record FilaTableroResponse(
        @Schema(description = "null para los reclamos todavía sin área") ReferenciaResponse area,
        ReferenciaResponse barrio,
        long pendientes,
        long vencidos) {
}
