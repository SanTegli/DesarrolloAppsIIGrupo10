package ar.edu.uade.reclamos.dominio.reporte;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;

/** Proyección: cuántos reclamos hay en un estado. */
public record ConteoPorEstado(EstadoReclamo estado, Long cantidad) {
}
