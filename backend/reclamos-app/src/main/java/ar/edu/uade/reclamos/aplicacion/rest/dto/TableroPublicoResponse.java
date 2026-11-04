package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Tablero público: solo cifras, ningún dato de personas. */
public record TableroPublicoResponse(
        LocalDate desde,
        LocalDate hasta,
        Long barrioId,
        Long categoriaId,
        @Schema(description = "Reclamos creados en el período, los dos días incluidos") long creadosEnPeriodo,
        @Schema(description = "Hoy en INGRESADO, ASIGNADO o EN_PROCESO") long pendientes,
        @Schema(description = "Hoy en RESUELTO") long resueltos,
        @Schema(description = "Hoy en CERRADO") long cerrados,
        long rechazados,
        long cancelados,
        @Schema(description = "Resueltos y cerrados sobre pendientes, resueltos y cerrados, con un decimal; "
                + "null si no hay ninguno (sin casos)") Double porcentajeAtendidos) {
}
