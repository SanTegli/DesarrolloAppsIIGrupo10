package ar.edu.uade.reclamos.aplicacion.servicio;

/**
 * La sugerencia de ubicación que el ciudadano eligió en el alta, tal como la mandó la interfaz. El backend la
 * valida antes de guardarla: no confía en coordenadas que llegan del navegador.
 */
public record UbicacionElegida(double latitud, double longitud, String direccionNormalizada) { }
