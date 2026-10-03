package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import jakarta.validation.constraints.*;

public record CambiarEstadoRequest(@NotNull EstadoReclamo estado,
                                   @Size(max = 1000) String observacion) { }
