package ar.edu.uade.reclamos.dominio.evento;

import ar.edu.uade.reclamos.dominio.modelo.OrigenPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;

/**
 * La IA revisó en segundo plano la prioridad de un reclamo que se creó sin su respuesta. Se publica aunque la
 * prioridad no cambie, para la auditoría; los avisos salen solo si {@link #cambio()}.
 *
 * @param origen el origen final: {@code IA} si se aplicó la sugerencia, {@code CATEGORIA} si la actual era mayor
 */
public record PrioridadRevisada(
        String numeroReclamo,
        Prioridad prioridadAnterior,
        Prioridad prioridadNueva,
        OrigenPrioridad origen,
        LocalDateTime ocurridoEn) implements EventoDominio {

    public boolean cambio() {
        return prioridadAnterior != prioridadNueva;
    }
}
