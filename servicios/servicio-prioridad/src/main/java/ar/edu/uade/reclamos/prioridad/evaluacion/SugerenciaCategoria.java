package ar.edu.uade.reclamos.prioridad.evaluacion;

/**
 * La categoría que sugiere el modelo.
 *
 * @param categoria una de las categorías recibidas, o {@code null} si ninguna corresponde
 */
public record SugerenciaCategoria(String categoria, String motivo, String proveedor, String modelo) { }
