package ar.edu.uade.reclamos.aplicacion.estrategia;

import ar.edu.uade.reclamos.aplicacion.integracion.ClienteServicioPrioridad;
import ar.edu.uade.reclamos.aplicacion.integracion.SugerenciaPrioridad;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.estrategia.PrioridadCalculada;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.OrigenPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;

/**
 * La prioridad la sugiere la IA, a través de REST 2. Como con las palabras clave, la IA puede subir la
 * prioridad de la categoría pero no bajarla: un riesgo que el municipio ya considera alto no queda abajo
 * porque el modelo lo interpretó distinto.
 *
 * <p>Si el servicio falla lanza {@link ServicioExternoNoDisponibleException}; el respaldo lo agrega
 * {@link PrioridadConRespaldo}, que es como se configura en la aplicación.
 */
public class PrioridadPorIA implements EstrategiaPrioridad {

    private final ClienteServicioPrioridad cliente;

    public PrioridadPorIA(ClienteServicioPrioridad cliente) {
        this.cliente = cliente;
    }

    @Override
    public PrioridadCalculada calcular(Reclamo reclamo) {
        Prioridad base = reclamo.getCategoria().getPrioridadBase();
        SugerenciaPrioridad sugerencia = cliente.sugerir(reclamo.getCategoria().getNombre(),
                reclamo.getDescripcion());
        if (base.esMayorQue(sugerencia.prioridad())) {
            return new PrioridadCalculada(base, OrigenPrioridad.CATEGORIA, "La IA sugirió "
                    + sugerencia.prioridad() + "; se mantiene la prioridad de la categoría, que es mayor.");
        }
        return new PrioridadCalculada(sugerencia.prioridad(), OrigenPrioridad.IA, sugerencia.motivo());
    }
}
