package ar.edu.uade.reclamos.aplicacion.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(int status, String codigo, String mensaje, List<String> detalles,
                            String ruta, LocalDateTime fecha) { }
