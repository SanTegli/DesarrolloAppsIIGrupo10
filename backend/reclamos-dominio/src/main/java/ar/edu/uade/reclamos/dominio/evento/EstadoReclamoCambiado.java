package ar.edu.uade.reclamos.dominio.evento;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import java.time.LocalDateTime;

/** El reclamo pasó de un estado a otro por acción de un usuario. */
public record EstadoReclamoCambiado(
        String numeroReclamo,
        EstadoReclamo estadoAnterior,
        EstadoReclamo estadoNuevo,
        Long usuarioId,
        LocalDateTime ocurridoEn) implements EventoDominio {
}
