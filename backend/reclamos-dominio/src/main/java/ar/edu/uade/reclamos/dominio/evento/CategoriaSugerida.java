package ar.edu.uade.reclamos.dominio.evento;

import java.time.LocalDateTime;

/**
 * La IA respondió qué categoría corresponde a un reclamo de "Otro problema" (PR 26). Es solo una sugerencia: queda
 * para la auditoría y para que el administrador la vea al recategorizar; no genera avisos.
 *
 * @param categoriaId {@code null} si la IA no sugirió ninguna o no respondió
 */
public record CategoriaSugerida(
        String numeroReclamo,
        Long categoriaId,
        String categoria,
        LocalDateTime ocurridoEn) implements EventoDominio {
}
