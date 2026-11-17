package ar.edu.uade.reclamos.aplicacion.integracion;

/**
 * Lo que respondió REST 2 sobre la categoría de un reclamo de "Otro problema", ya validado.
 *
 * @param categoria el nombre de una de las categorías enviadas, o {@code null} si ninguna corresponde
 */
public record SugerenciaCategoria(String categoria, String motivo, String proveedor, String modelo) { }
