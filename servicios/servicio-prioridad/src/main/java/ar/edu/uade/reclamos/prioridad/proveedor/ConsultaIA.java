package ar.edu.uade.reclamos.prioridad.proveedor;

/**
 * Lo que se le manda al modelo, igual para todos los proveedores.
 *
 * @param instrucciones criterios de prioridad y reglas de respuesta (mensaje de sistema)
 * @param reclamo categoría y descripción del reclamo, delimitadas como datos (mensaje del usuario)
 */
public record ConsultaIA(String instrucciones, String reclamo) { }
