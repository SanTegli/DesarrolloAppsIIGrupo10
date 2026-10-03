package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;

public record ResumenReclamoResponse(String numero, String direccion, EstadoReclamo estado,
                                     Prioridad prioridad, boolean vencido, LocalDateTime fechaCreacion,
                                     LocalDateTime fechaLimite, ReferenciaResponse categoria,
                                     ReferenciaResponse barrio, UsuarioReferenciaResponse ciudadano,
                                     ReferenciaResponse area) { }
