package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificacionSimuladaRequest(@Schema(example = "RU4K7P2Q") @NotBlank @Size(max = 8) String codigo,
                                          @Schema(example = "+5491155550000") @NotBlank @Size(max = 25)
                                          String telefono) { }
