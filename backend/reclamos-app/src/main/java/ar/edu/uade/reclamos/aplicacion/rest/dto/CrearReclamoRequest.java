package ar.edu.uade.reclamos.aplicacion.rest.dto;

import jakarta.validation.constraints.*;

public record CrearReclamoRequest(
        @NotNull @Positive Long categoriaId,
        @NotNull @Positive Long barrioId,
        @NotBlank @Size(max = 1000) String descripcion,
        @NotBlank @Size(max = 200) String direccion) { }
