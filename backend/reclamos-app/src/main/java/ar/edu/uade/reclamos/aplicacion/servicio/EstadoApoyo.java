package ar.edu.uade.reclamos.aplicacion.servicio;

/** Cómo quedó el apoyo después de apoyar o retirar: cuántos lo apoyan y si quien llamó es uno de ellos. */
public record EstadoApoyo(String numero, long apoyos, boolean apoyado) {
}
