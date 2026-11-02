package ar.edu.uade.reclamos.aplicacion.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** {@code ubicacion} es opcional: sin ella, el reclamo queda con la ubicación pendiente. */
public record CrearReclamoRequest(
        @NotNull @Positive Long categoriaId,
        @NotNull @Positive Long barrioId,
        @NotBlank @Size(max = 1000) String descripcion,
        @NotBlank @Size(max = 200) String direccion,
        @Valid UbicacionElegidaRequest ubicacion) { }
