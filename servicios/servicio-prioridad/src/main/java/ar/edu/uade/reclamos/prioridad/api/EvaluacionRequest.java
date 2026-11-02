package ar.edu.uade.reclamos.prioridad.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Solo la categoría y la descripción: el servicio nunca recibe nombre, email, DNI, teléfono, dirección ni foto. */
public record EvaluacionRequest(
        @Schema(example = "Luminaria rota") @NotBlank @Size(max = 80) String categoria,
        @Schema(example = "Poste inclinado con cables expuestos frente a la escuela")
        @NotBlank @Size(max = 1000) String descripcion) { }
