package ar.edu.uade.reclamos.aplicacion.integracion;

import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.util.List;
import java.util.Optional;

/**
 * Compara el reclamo que un vecino está por crear con los reclamos abiertos cercanos (PR 27). Hay dos
 * implementaciones: {@link #NINGUNO}, sin IA, y {@link ComparadorDuplicadosPorIA}, con REST 2.
 */
public interface ComparadorDuplicados {

    /** Sin la estrategia {@code ia}: no hay comparación y quedan los de la misma categoría, como antes. */
    ComparadorDuplicados NINGUNO = (categoria, descripcion, candidatos) -> Optional.empty();

    /**
     * @return vacío si no hay IA o no respondió a tiempo; si respondió, el más parecido (o ninguno) y el motivo
     */
    Optional<Resultado> comparar(String categoria, String descripcion, List<Reclamo> candidatos);

    /**
     * @param similar el más parecido, o {@code null} si la IA dijo que ninguno es el mismo problema
     */
    record Resultado(Reclamo similar, String motivo) { }
}
