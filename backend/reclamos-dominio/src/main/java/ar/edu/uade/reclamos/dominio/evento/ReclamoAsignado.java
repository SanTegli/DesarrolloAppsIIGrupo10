package ar.edu.uade.reclamos.dominio.evento;

import java.time.LocalDateTime;

/**
 * El reclamo quedó a cargo de un área, por asignación automática o manual.
 *
 * @param usuarioId administrador que asignó; {@code null} si fue automática
 */
public record ReclamoAsignado(
        String numeroReclamo,
        Long areaId,
        String areaNombre,
        Long usuarioId,
        LocalDateTime ocurridoEn) implements EventoDominio {

    public boolean fueAutomatica() {
        return usuarioId == null;
    }
}
