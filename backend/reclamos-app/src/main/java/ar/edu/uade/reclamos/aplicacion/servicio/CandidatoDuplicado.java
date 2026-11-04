package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.dominio.modelo.Reclamo;

/**
 * Un reclamo abierto cerca del punto elegido en el alta, de la misma categoría.
 *
 * @param distanciaMetros distancia al punto elegido
 * @param apoyos          cuántos vecinos ya lo apoyan
 * @param apoyadoPorMi    si quien consulta ya lo apoya
 * @param propio          si lo creó quien consulta (no se puede apoyar)
 */
public record CandidatoDuplicado(Reclamo reclamo, double distanciaMetros, long apoyos, boolean apoyadoPorMi,
                                 boolean propio) {
}
