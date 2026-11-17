package ar.edu.uade.reclamos.aplicacion.rest.dto;

import jakarta.validation.constraints.*;

/**
 * PR 27: el reclamo que el vecino está por crear, para buscar posibles duplicados. Va en el cuerpo y no en la URL
 * porque lleva la descripción.
 */
public record BuscarDuplicadosRequest(
        @NotNull @Positive Long categoriaId,
        @Size(max = 1000) String descripcion,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitud,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitud) { }
