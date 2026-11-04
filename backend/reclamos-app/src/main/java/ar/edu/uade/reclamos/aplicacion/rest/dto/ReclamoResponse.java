package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.OrigenPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** @param foto ruta de la foto ({@code /api/reclamos/{numero}/foto}); {@code null} si el reclamo no tiene */
public record ReclamoResponse(String numero, String descripcion, String direccion, UbicacionResponse ubicacion,
                              String foto,
                              EstadoReclamo estado, Prioridad prioridad, OrigenPrioridad origenPrioridad,
                              String motivoPrioridad, boolean vencido, boolean asignacionPendiente,
                              LocalDateTime fechaCreacion, LocalDateTime fechaLimite, ReferenciaResponse categoria,
                              ReferenciaResponse barrio, UsuarioReferenciaResponse ciudadano, ReferenciaResponse area,
                              UsuarioReferenciaResponse agente, List<HistorialEstadoResponse> historial,
                              Set<EstadoReclamo> accionesDisponibles) { }
