package ar.edu.uade.reclamos.prioridad.evaluacion;

/** Prioridad sugerida, ya validada, y quién la sugirió. */
public record Evaluacion(Prioridad prioridad, String motivo, String proveedor, String modelo) { }
