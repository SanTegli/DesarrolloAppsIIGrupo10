package ar.edu.uade.reclamos.aplicacion.verificacion;

import java.time.LocalDateTime;

/** El código que la interfaz le muestra al ciudadano, con el enlace del canal y cuándo vence. */
public record CodigoEmitido(String codigo, String enlace, LocalDateTime vence, String modo) { }
