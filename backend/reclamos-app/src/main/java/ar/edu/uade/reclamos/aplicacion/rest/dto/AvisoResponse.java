package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.CanalNotificacion;
import java.time.LocalDateTime;

/**
 * Un aviso en la bandeja de un usuario. Lleva el número del reclamo para poder abrirlo.
 *
 * @param abrible {@code false} si es de un reclamo ajeno que el vecino apoya (PR 27): no puede ver su detalle
 */
public record AvisoResponse(String numeroReclamo, CanalNotificacion canal, String mensaje,
                            LocalDateTime fechaEnvio, boolean abrible) { }
