package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.CanalNotificacion;
import java.time.LocalDateTime;

public record NotificacionResponse(CanalNotificacion canal, UsuarioReferenciaResponse destinatario,
                                   String mensaje, LocalDateTime fechaEnvio) { }
