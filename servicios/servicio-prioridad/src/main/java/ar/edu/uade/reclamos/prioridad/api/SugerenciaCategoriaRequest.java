package ar.edu.uade.reclamos.prioridad.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lo que escribió el vecino al elegir "Otro problema" y las categorías entre las que se elige. Como en la
 * prioridad, nada que identifique a una persona: ni nombre, ni email, ni dirección, ni foto.
 */
public record SugerenciaCategoriaRequest(
        @Schema(example = "Baldosas levantadas") @NotBlank @Size(max = 80) String categoriaPropuesta,
        @Schema(example = "En la vereda de la escuela hay baldosas sueltas y ya se cayó una señora")
        @NotBlank @Size(max = 1000) String descripcion,
        @ArraySchema(schema = @Schema(example = "Vereda rota", maxLength = 80), minItems = 1, maxItems = 30)
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 80) String> categorias) { }
