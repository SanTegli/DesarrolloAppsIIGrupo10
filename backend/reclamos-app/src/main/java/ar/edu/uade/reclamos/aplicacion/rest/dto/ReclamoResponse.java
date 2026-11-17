package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.OrigenPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * @param foto           ruta de la foto ({@code /api/reclamos/{numero}/foto}); {@code null} si el reclamo no tiene
 * @param cantidadApoyos cuántos vecinos apoyan el reclamo (PR 17)
 * @param categoriaPropuesta qué tipo de problema escribió el vecino al elegir "Otro problema" (PR 26)
 * @param categoriaSugerida  la que sugirió la IA; {@code null} si no sugirió ninguna o todavía no respondió
 * @param motivoSugerencia   por qué; {@code null} mientras la IA no respondió
 */
public record ReclamoResponse(String numero, String descripcion, String direccion, UbicacionResponse ubicacion,
                              String foto, long cantidadApoyos,
                              EstadoReclamo estado, Prioridad prioridad, OrigenPrioridad origenPrioridad,
                              String motivoPrioridad, boolean vencido, boolean asignacionPendiente,
                              LocalDateTime fechaCreacion, LocalDateTime fechaLimite, ReferenciaResponse categoria,
                              ReferenciaResponse municipio, UsuarioReferenciaResponse ciudadano, ReferenciaResponse area,
                              UsuarioReferenciaResponse agente, List<HistorialEstadoResponse> historial,
                              Set<EstadoReclamo> accionesDisponibles, String categoriaPropuesta,
                              ReferenciaResponse categoriaSugerida, String motivoSugerencia) { }
