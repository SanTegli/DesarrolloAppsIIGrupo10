package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** Tablero de gestión, para agentes (su área) y administradores (todas). */
public record TableroGestionResponse(
        LocalDateTime fecha,
        @Schema(description = "El área del agente; null para el administrador") ReferenciaResponse area,
        long pendientes,
        @Schema(description = "Pendientes que pasaron su fecha límite, calculados por fecha") long vencidos,
        @Schema(description = "Pendientes sin área; null para un agente") Long sinAsignar,
        @Schema(description = "Vencidos sobre pendientes, con un decimal; null sin pendientes (sin casos)")
        Double porcentajeVencidos,
        @Schema(description = "Por área y barrio, primero los que tienen más vencidos") List<FilaTableroResponse> filas) {
}
