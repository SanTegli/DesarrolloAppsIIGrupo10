package ar.edu.uade.reclamos.dominio.evento;

import java.time.LocalDateTime;

/**
 * Hecho relevante que ya ocurrió en el dominio. Los eventos llevan solo identificadores y datos
 * simples para poder serializarse a JSON si hace falta publicarlos fuera del proceso.
 */
public interface EventoDominio {

    String numeroReclamo();

    LocalDateTime ocurridoEn();

    /** Nombre del evento, por ejemplo {@code ReclamoCreado}. */
    default String nombre() {
        return getClass().getSimpleName();
    }
}
