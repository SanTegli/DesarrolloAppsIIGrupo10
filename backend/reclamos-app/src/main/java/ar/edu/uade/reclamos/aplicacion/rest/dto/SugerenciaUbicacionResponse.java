package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una interpretación posible de la dirección, con coordenadas. Se manda tal cual en el alta si se elige. */
public record SugerenciaUbicacionResponse(
        @Schema(example = "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires") String direccionNormalizada,
        @Schema(example = "-34.73614578") double latitud,
        @Schema(example = "-58.3228998575") double longitud) { }
