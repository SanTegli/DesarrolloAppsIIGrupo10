package ar.edu.uade.reclamos.prioridad.api;

import java.time.LocalDateTime;
import java.util.List;

/** El mismo cuerpo de error que REST 1, para que el backend y Swagger los lean igual. */
public record ErrorResponse(int status, String codigo, String mensaje, List<String> detalles, String ruta,
                            LocalDateTime fecha) { }
