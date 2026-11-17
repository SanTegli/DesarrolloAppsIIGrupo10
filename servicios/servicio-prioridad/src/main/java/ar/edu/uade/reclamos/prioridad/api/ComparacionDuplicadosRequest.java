package ar.edu.uade.reclamos.prioridad.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * El reclamo que el vecino está por crear y los reclamos abiertos que el backend encontró a pocos metros. Solo
 * categoría y descripción: ni nombres, ni direcciones, ni fotos, ni números de reclamo.
 */
public record ComparacionDuplicadosRequest(
        @NotNull @Valid Reclamo nuevo,
        @ArraySchema(minItems = 1, maxItems = 10) @NotEmpty @Size(max = 10) List<@Valid @NotNull Candidato> candidatos) {

    /** El reclamo nuevo, todavía sin crear. */
    public record Reclamo(
            @Schema(example = "Luminaria rota") @NotBlank @Size(max = 80) String categoria,
            @Schema(example = "El farol de la esquina no prende hace tres días")
            @NotBlank @Size(max = 1000) String descripcion) { }

    /**
     * Un reclamo abierto cercano.
     *
     * @param id lo elige el backend (por ejemplo, "1", "2"); la respuesta lo devuelve en {@code similar}
     */
    public record Candidato(
            @Schema(example = "1") @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+") String id,
            @Schema(example = "Otro problema") @NotBlank @Size(max = 80) String categoria,
            @Schema(example = "No hay luz en la esquina, el poste está apagado")
            @NotBlank @Size(max = 1000) String descripcion) { }
}
