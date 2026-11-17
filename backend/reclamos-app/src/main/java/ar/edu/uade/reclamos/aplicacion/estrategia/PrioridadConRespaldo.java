package ar.edu.uade.reclamos.aplicacion.estrategia;

import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.estrategia.PrioridadCalculada;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.OrigenPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>Decorator</b> de una {@link EstrategiaPrioridad} que depende de un servicio externo: tiene la misma
 * interfaz, la envuelve y le agrega el respaldo. Si la estrategia envuelta no puede responder, usa la de
 * respaldo y lo deja marcado en el origen, para que gestión vea que la IA no intervino.
 *
 * <p>Hay dos variantes:
 * <ul>
 *   <li>{@link #PrioridadConRespaldo(EstrategiaPrioridad, EstrategiaPrioridad)}: el respaldo es definitivo,
 *       con origen {@code RESPALDO}.</li>
 *   <li>{@link #conRevisionPosterior}: el respaldo es provisorio, con origen {@code PENDIENTE_IA}. Un
 *       observador del alta pide la reevaluación por la cola y la IA la completa unos segundos después.</li>
 * </ul>
 *
 * <p>Así el manejo de fallas queda en una sola clase, y {@code ReclamoService} nunca ve la falla: el reclamo
 * siempre se crea.
 */
public class PrioridadConRespaldo implements EstrategiaPrioridad {

    static final String MOTIVO = "La IA no estaba disponible; se usó la prioridad de la categoría.";
    static final String MOTIVO_PENDIENTE = "La IA no respondió a tiempo: por ahora vale la prioridad de la "
            + "categoría y la IA lo vuelve a revisar en unos segundos.";
    private static final Logger LOGGER = LoggerFactory.getLogger(PrioridadConRespaldo.class);

    private final EstrategiaPrioridad principal;
    private final EstrategiaPrioridad respaldo;
    private final OrigenPrioridad origenSiFalla;
    private final String motivoSiFalla;

    /** Si la estrategia principal falla, la prioridad de respaldo queda como definitiva. */
    public PrioridadConRespaldo(EstrategiaPrioridad principal, EstrategiaPrioridad respaldo) {
        this(principal, respaldo, OrigenPrioridad.RESPALDO, MOTIVO);
    }

    private PrioridadConRespaldo(EstrategiaPrioridad principal, EstrategiaPrioridad respaldo,
                                 OrigenPrioridad origenSiFalla, String motivoSiFalla) {
        this.principal = principal;
        this.respaldo = respaldo;
        this.origenSiFalla = origenSiFalla;
        this.motivoSiFalla = motivoSiFalla;
    }

    /** Si la estrategia principal falla, la prioridad de respaldo es provisoria hasta que la IA la revise. */
    public static PrioridadConRespaldo conRevisionPosterior(EstrategiaPrioridad principal,
                                                            EstrategiaPrioridad respaldo) {
        return new PrioridadConRespaldo(principal, respaldo, OrigenPrioridad.PENDIENTE_IA, MOTIVO_PENDIENTE);
    }

    @Override
    public PrioridadCalculada calcular(Reclamo reclamo) {
        try {
            return principal.calcular(reclamo);
        } catch (ServicioExternoNoDisponibleException error) {
            LOGGER.warn("Prioridad de respaldo ({}) para el reclamo {}: {}", origenSiFalla, reclamo.getNumero(),
                    error.getMessage());
            return new PrioridadCalculada(respaldo.calcular(reclamo).prioridad(), origenSiFalla, motivoSiFalla);
        }
    }
}
