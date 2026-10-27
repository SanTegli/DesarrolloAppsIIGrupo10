package ar.edu.uade.reclamos.dominio.estrategia;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.validacion.Validador;
import ar.edu.uade.reclamos.dominio.modelo.OrigenPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;

/**
 * Resultado de una {@link EstrategiaPrioridad}: la prioridad, qué la decidió y, si hay, por qué.
 *
 * @param motivo explicación breve para gestión; {@code null} cuando la prioridad es la de la categoría
 */
public record PrioridadCalculada(Prioridad prioridad, OrigenPrioridad origen, String motivo) {
    public static final int LONGITUD_MAXIMA_MOTIVO = 300;

    public PrioridadCalculada {
        Validador.requerirNoNulo(prioridad, "prioridad");
        Validador.requerirNoNulo(origen, "origen");
        motivo = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (motivo != null && motivo.length() > LONGITUD_MAXIMA_MOTIVO) {
            throw new DatosInvalidosException(
                    "El motivo de la prioridad no puede superar los " + LONGITUD_MAXIMA_MOTIVO + " caracteres.");
        }
    }

    /** La prioridad base de la categoría, sin motivo. */
    public static PrioridadCalculada porCategoria(Prioridad base) {
        return new PrioridadCalculada(base, OrigenPrioridad.CATEGORIA, null);
    }
}
