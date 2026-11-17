package ar.edu.uade.reclamos.dominio.evento;

import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;

/**
 * Un ciudadano ingresó un reclamo nuevo.
 *
 * @param categoriaOtra el vecino eligió "Otro problema" (PR 26): la IA tiene que sugerir la categoría
 */
public record ReclamoCreado(
        String numeroReclamo,
        Long ciudadanoId,
        Long categoriaId,
        Long municipioId,
        boolean categoriaOtra,
        Prioridad prioridad,
        LocalDateTime ocurridoEn) implements EventoDominio {
}
