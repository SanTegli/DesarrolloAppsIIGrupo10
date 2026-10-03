package ar.edu.uade.reclamos.dominio.evento;

import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;

/**
 * El reclamo superó su fecha límite sin resolverse y se le subió la prioridad.
 *
 * @param areaId área a cargo; {@code null} si el reclamo seguía sin asignar
 */
public record ReclamoVencido(
        String numeroReclamo,
        Long areaId,
        Prioridad prioridadNueva,
        LocalDateTime fechaLimite,
        LocalDateTime ocurridoEn) implements EventoDominio {
}
