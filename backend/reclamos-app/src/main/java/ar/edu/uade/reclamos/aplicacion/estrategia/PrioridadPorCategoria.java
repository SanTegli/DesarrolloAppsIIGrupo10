package ar.edu.uade.reclamos.aplicacion.estrategia;

import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;

public class PrioridadPorCategoria implements EstrategiaPrioridad {
    @Override
    public Prioridad calcular(Reclamo reclamo) {
        return reclamo.getCategoria().getPrioridadBase();
    }
}
