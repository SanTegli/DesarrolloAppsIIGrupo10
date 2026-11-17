package ar.edu.uade.reclamos.aplicacion.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * {@code ubicacion} es opcional: sin ella, el reclamo queda con la ubicación pendiente. {@code municipioId} es
 * el de la sugerencia elegida o, si el mapa no respondió, el que eligió el ciudadano de la lista.
 * {@code categoriaPropuesta} (PR 26) es qué tipo de problema es, en palabras del vecino: obligatoria si la
 * categoría es "Otro problema" y se ignora en las demás.
 */
public record CrearReclamoRequest(
        @NotNull @Positive Long categoriaId,
        @NotNull @Positive Long municipioId,
        @NotBlank @Size(max = 1000) String descripcion,
        @NotBlank @Size(max = 200) String direccion,
        @Valid UbicacionElegidaRequest ubicacion,
        @Size(max = 80) String categoriaPropuesta) { }
