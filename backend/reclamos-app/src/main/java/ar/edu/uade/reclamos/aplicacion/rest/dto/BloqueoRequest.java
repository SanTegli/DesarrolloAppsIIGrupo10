package ar.edu.uade.reclamos.aplicacion.rest.dto;

import jakarta.validation.constraints.NotNull;

public record BloqueoRequest(@NotNull Boolean bloqueado) { }
