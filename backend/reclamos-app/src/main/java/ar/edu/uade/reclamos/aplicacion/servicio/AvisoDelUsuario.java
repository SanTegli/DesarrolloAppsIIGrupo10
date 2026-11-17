package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.dominio.modelo.Notificacion;

/**
 * Un aviso de la bandeja.
 *
 * @param abrible si el usuario puede abrir el detalle del reclamo; {@code false} en los reclamos que apoya (PR 27)
 */
public record AvisoDelUsuario(Notificacion aviso, boolean abrible) { }
