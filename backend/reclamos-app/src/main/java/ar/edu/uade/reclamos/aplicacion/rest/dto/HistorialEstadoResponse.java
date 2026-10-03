package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import java.time.LocalDateTime;

public record HistorialEstadoResponse(EstadoReclamo estadoAnterior, EstadoReclamo estadoNuevo,
                                      LocalDateTime fecha, String observacion,
                                      UsuarioReferenciaResponse usuario) { }
