package ar.edu.uade.reclamos.aplicacion.rest.dto;

/** Cómo quedó el apoyo: cuántos vecinos apoyan el reclamo y si quien llamó es uno de ellos. */
public record ApoyoResponse(String numero, long cantidadApoyos, boolean apoyado) {
}
