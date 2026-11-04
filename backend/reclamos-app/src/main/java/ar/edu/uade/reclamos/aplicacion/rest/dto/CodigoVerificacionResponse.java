package ar.edu.uade.reclamos.aplicacion.rest.dto;

import java.time.LocalDateTime;

/**
 * @param enlace enlace de Telegram con el código cargado; nulo en modo simulado
 * @param modo   {@code telegram} o {@code simulado}
 */
public record CodigoVerificacionResponse(String codigo, String enlace, LocalDateTime vence, String modo) { }
