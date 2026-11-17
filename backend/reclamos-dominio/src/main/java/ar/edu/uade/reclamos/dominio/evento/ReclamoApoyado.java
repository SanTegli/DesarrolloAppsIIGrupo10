package ar.edu.uade.reclamos.dominio.evento;

import java.time.LocalDateTime;

/**
 * Un vecino se sumó al reclamo de otro en lugar de crear uno repetido (PR 27: "Asociarme"). Desde ahí recibe los
 * avisos de los cambios de ese reclamo.
 */
public record ReclamoApoyado(String numeroReclamo, Long ciudadanoId, LocalDateTime ocurridoEn)
        implements EventoDominio {
}
