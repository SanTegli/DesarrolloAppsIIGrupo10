package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un municipio donde se pueden hacer reclamos. Cada uno tiene sus propias áreas. */
public record MunicipioResponse(@Schema(example = "1") Long id, @Schema(example = "Quilmes") String nombre,
                                @Schema(example = "Buenos Aires") String provincia) { }
