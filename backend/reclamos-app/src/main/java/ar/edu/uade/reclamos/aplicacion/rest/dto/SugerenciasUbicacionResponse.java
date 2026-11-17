package ar.edu.uade.reclamos.aplicacion.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Resultado de buscar una dirección en todos los municipios. Con {@code disponible: false} el geocodificador no
 * respondió para ninguno: el ciudadano elige el municipio de la lista y el reclamo se crea con la ubicación
 * pendiente.
 */
public record SugerenciasUbicacionResponse(
        @Schema(description = "false si el geocodificador no respondió para ningún municipio") boolean disponible,
        @Schema(description = "Hasta 5 opciones con coordenadas, cada una con su municipio; vacía si no se encontró")
        List<SugerenciaUbicacionResponse> sugerencias) { }
