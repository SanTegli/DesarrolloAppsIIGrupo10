package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.Prioridad;

/** @param otra es "Otro problema" (PR 26): el formulario pide que el vecino escriba qué tipo de problema es */
public record CategoriaResponse(Long id, String nombre, String descripcion, int slaHoras,
                                Prioridad prioridadBase, boolean otra) { }
