package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * La sugerencia de {@code GET /api/ubicaciones/sugerencias} que eligió el ciudadano. El backend comprueba que
 * caiga dentro del municipio del barrio.
 */
public record UbicacionElegidaRequest(
        @Schema(example = "-34.73614578") @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitud,
        @Schema(example = "-58.3228998575") @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitud,
        @Schema(example = "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires")
        @NotBlank @Size(max = 300) String direccionNormalizada) { }
