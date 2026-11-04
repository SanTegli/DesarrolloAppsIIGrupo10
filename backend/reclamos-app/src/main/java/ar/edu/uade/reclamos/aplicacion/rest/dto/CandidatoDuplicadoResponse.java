package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Tarjeta de un posible duplicado. A propósito no lleva el nombre ni ningún dato del autor: solo lo necesario
 * para reconocer el problema.
 */
public record CandidatoDuplicadoResponse(
        String numero,
        EstadoReclamo estado,
        LocalDateTime fechaCreacion,
        String descripcion,
        @Schema(description = "Dirección normalizada por el geocodificador") String direccion,
        @Schema(description = "Ruta de la foto; null si el reclamo no tiene") String foto,
        @Schema(description = "Distancia al punto elegido, en metros, redondeada") long distanciaMetros,
        @Schema(description = "Cuántos vecinos lo apoyan") long cantidadApoyos,
        @Schema(description = "Si quien consulta ya lo apoya") boolean apoyadoPorMi,
        @Schema(description = "Si lo creó quien consulta: no se puede apoyar") boolean propio) {
}
