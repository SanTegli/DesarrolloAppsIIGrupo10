package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.dominio.modelo.Notificacion;

/**
 * Un aviso de la bandeja.
 *
 * @param abrible si el usuario puede abrir el reclamo
 * @param seguimiento si el enlace abre el seguimiento de solo lectura
 */
public record AvisoDelUsuario(Notificacion aviso, boolean abrible, boolean seguimiento) { }
