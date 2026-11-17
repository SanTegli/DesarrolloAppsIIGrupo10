package ar.edu.uade.reclamos.dominio.evento;

import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;

/** Un ciudadano ingresó un reclamo nuevo. */
public record ReclamoCreado(
        String numeroReclamo,
        Long ciudadanoId,
        Long categoriaId,
        Long municipioId,
        Prioridad prioridad,
        LocalDateTime ocurridoEn) implements EventoDominio {
}
