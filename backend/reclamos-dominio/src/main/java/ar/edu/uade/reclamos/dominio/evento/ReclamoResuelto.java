package ar.edu.uade.reclamos.dominio.evento;

import java.time.LocalDateTime;

/** Un agente dio por resuelto el reclamo; falta la confirmación del ciudadano. */
public record ReclamoResuelto(
        String numeroReclamo,
        Long ciudadanoId,
        Long agenteId,
        LocalDateTime ocurridoEn) implements EventoDominio {
}
