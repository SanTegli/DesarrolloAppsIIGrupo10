package ar.edu.uade.reclamos.aplicacion.rest.dto;

import jakarta.validation.constraints.*;

public record AsignarReclamoRequest(@NotNull @Positive Long areaId,
                                   @Size(max = 1000) String observacion) { }
