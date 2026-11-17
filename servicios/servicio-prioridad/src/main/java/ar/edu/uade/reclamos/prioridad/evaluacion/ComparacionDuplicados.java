package ar.edu.uade.reclamos.prioridad.evaluacion;

/**
 * Qué reclamo cercano describe el mismo problema que el nuevo, según el modelo.
 *
 * @param similar el id de uno de los candidatos, o {@code null} si ninguno es el mismo problema
 */
public record ComparacionDuplicados(String similar, String motivo, String proveedor, String modelo) { }
