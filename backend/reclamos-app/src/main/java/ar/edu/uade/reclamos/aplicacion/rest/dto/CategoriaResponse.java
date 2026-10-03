package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.Prioridad;

public record CategoriaResponse(Long id, String nombre, String descripcion, int slaHoras,
                                Prioridad prioridadBase) { }
