package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ReclamoResponse(String numero, String descripcion, String direccion, EstadoReclamo estado,
                              Prioridad prioridad, boolean vencido, LocalDateTime fechaCreacion,
                              LocalDateTime fechaLimite, ReferenciaResponse categoria, ReferenciaResponse barrio,
                              UsuarioReferenciaResponse ciudadano, ReferenciaResponse area,
                              UsuarioReferenciaResponse agente, List<HistorialEstadoResponse> historial,
                              Set<EstadoReclamo> accionesDisponibles) { }
