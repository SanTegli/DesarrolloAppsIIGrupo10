package ar.edu.uade.reclamos.aplicacion.rest.dto;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import java.time.LocalDateTime;
import java.util.List;

public record SeguimientoReclamoResponse(String numero, EstadoReclamo estado, String categoria,
                                         String descripcion, String foto, List<Cambio> historial) {
    public record Cambio(EstadoReclamo estadoNuevo, LocalDateTime fecha) { }
}
