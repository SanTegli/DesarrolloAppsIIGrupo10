package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@Schema(example = "ana@example.org") @NotBlank @Size(max = 254) String email,
                           @Schema(example = "demo1234") @NotBlank @Size(max = 200) String contrasena) { }
