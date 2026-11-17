package ar.edu.uade.reclamos.aplicacion.servicio;

import java.util.List;

/**
 * Lo que ve el vecino antes de crear el reclamo (PR 27).
 *
 * @param comparadoPorIA la IA comparó los reclamos cercanos; {@code false} si no hay IA o no respondió, y entonces
 *                       los candidatos son los de la misma categoría, como antes
 * @param motivo         la explicación de la IA, o {@code null} sin comparación
 * @param candidatos     primero el más parecido, si la IA eligió uno; después los de la misma categoría, del más
 *                       cercano al más lejano
 */
public record DuplicadosDelAlta(boolean comparadoPorIA, String motivo, List<CandidatoDuplicado> candidatos) {

    public DuplicadosDelAlta {
        candidatos = List.copyOf(candidatos);
    }
}
