package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Pendientes y vencidos de un área en un municipio. */
public record FilaTableroResponse(
        @Schema(description = "null para los reclamos todavía sin área") ReferenciaResponse area,
        ReferenciaResponse municipio,
        long pendientes,
        long vencidos) {
}
