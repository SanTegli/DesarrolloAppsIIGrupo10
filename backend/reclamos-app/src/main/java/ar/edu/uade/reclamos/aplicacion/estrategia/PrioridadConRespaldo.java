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
 * respaldo y marca el origen {@code RESPALDO}, para que gestión vea que la IA no intervino.
 *
 * <p>Así el manejo de fallas queda en una sola clase, y {@code ReclamoService} nunca ve la falla: el reclamo
 * siempre se crea.
 */
public class PrioridadConRespaldo implements EstrategiaPrioridad {

    static final String MOTIVO = "La IA no estaba disponible; se usó la prioridad de la categoría.";
    private static final Logger LOGGER = LoggerFactory.getLogger(PrioridadConRespaldo.class);

    private final EstrategiaPrioridad principal;
    private final EstrategiaPrioridad respaldo;

    public PrioridadConRespaldo(EstrategiaPrioridad principal, EstrategiaPrioridad respaldo) {
        this.principal = principal;
        this.respaldo = respaldo;
    }

    @Override
    public PrioridadCalculada calcular(Reclamo reclamo) {
        try {
            return principal.calcular(reclamo);
        } catch (ServicioExternoNoDisponibleException error) {
            LOGGER.warn("Prioridad de respaldo para el reclamo {}: {}", reclamo.getNumero(), error.getMessage());
            return new PrioridadCalculada(respaldo.calcular(reclamo).prioridad(), OrigenPrioridad.RESPALDO, MOTIVO);
        }
    }
}
