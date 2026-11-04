package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import java.time.Duration;

/**
 * Cuántas veces y hasta cuándo un ciudadano puede reabrir un reclamo resuelto. Los dos números se configuran;
 * el dominio solo aplica la regla.
 *
 * @param maximo cuántas reaperturas directas admite cada reclamo
 * @param plazo  cuánto tiempo después de la última resolución todavía se puede reabrir
 */
public record PoliticaReapertura(int maximo, Duration plazo) {

    /** Una reapertura, dentro de los 7 días de la última resolución. */
    public static final PoliticaReapertura PREDETERMINADA = new PoliticaReapertura(1, Duration.ofDays(7));

    public PoliticaReapertura {
        if (maximo < 0) {
            throw new DatosInvalidosException("La cantidad máxima de reaperturas no puede ser negativa.");
        }
        if (plazo == null || plazo.isZero() || plazo.isNegative()) {
            throw new DatosInvalidosException("El plazo para reabrir tiene que ser positivo.");
        }
    }

    /** "1 día" o "7 días", para los mensajes. */
    String plazoEnDias() {
        long dias = Math.max(1, plazo.toDays());
        return dias == 1 ? "1 día" : dias + " días";
    }
}
