package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.Rol;

public record UsuarioResponse(Long id, String nombreCompleto, Rol rol, ReferenciaResponse area) { }
