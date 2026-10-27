package ar.edu.uade.reclamos.aplicacion.estrategia;

import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.estrategia.PrioridadCalculada;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;

/** La prioridad base de la categoría. */
public class PrioridadPorCategoria implements EstrategiaPrioridad {

    @Override
    public PrioridadCalculada calcular(Reclamo reclamo) {
        return PrioridadCalculada.porCategoria(reclamo.getCategoria().getPrioridadBase());
    }
}
