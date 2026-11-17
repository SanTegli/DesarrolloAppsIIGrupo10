package ar.edu.uade.reclamos.dominio.evento;

import java.time.LocalDateTime;

/**
 * Un administrador cambió la categoría del reclamo (PR 26), típicamente uno de "Otro problema". Después del
 * cambio el sistema intenta asignarlo a un área de la categoría nueva.
 *
 * @param sugeridaPorIA la categoría nueva es la que había sugerido la IA, para medir cuánto acierta
 */
public record ReclamoRecategorizado(
        String numeroReclamo,
        Long categoriaAnteriorId,
        String categoriaAnterior,
        Long categoriaNuevaId,
        String categoriaNueva,
        boolean sugeridaPorIA,
        Long usuarioId,
        LocalDateTime ocurridoEn) implements EventoDominio {
}
