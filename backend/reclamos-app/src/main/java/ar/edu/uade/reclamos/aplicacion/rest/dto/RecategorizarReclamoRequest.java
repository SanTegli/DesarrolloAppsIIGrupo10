package ar.edu.uade.reclamos.aplicacion.rest.dto;

import jakarta.validation.constraints.*;

/** PR 26: la categoría nueva de un reclamo todavía sin área, típicamente uno de "Otro problema". */
public record RecategorizarReclamoRequest(@NotNull @Positive Long categoriaId,
                                          @Size(max = 1000) String observacion) { }
