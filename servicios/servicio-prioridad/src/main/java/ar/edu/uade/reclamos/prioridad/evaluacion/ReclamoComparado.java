package ar.edu.uade.reclamos.prioridad.evaluacion;

/**
 * Un reclamo tal como lo ve la comparación de duplicados: solo categoría y descripción.
 *
 * @param id lo pone el backend para reconocer la respuesta; en el reclamo nuevo no se usa
 */
public record ReclamoComparado(String id, String categoria, String descripcion) { }
