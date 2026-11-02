package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Resultado de buscar una dirección. Con {@code disponible: false} el geocodificador no respondió: el reclamo
 * se puede crear igual, con la ubicación pendiente.
 */
public record SugerenciasUbicacionResponse(
        @Schema(description = "false si el geocodificador no respondió") boolean disponible,
        @Schema(description = "Hasta 5 opciones con coordenadas dentro del municipio; vacía si no se encontró")
        List<SugerenciaUbicacionResponse> sugerencias) { }
