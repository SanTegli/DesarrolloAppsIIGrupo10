package ar.edu.uade.reclamos.dominio.estrategia;

import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;

/**
 * Patrón Strategy: algoritmo intercambiable para calcular la prioridad de un reclamo recién creado.
 *
 * <p>Implementaciones del Hito 1 (PR 3): {@code PrioridadPorCategoria} y {@code PrioridadPorPalabrasClave}.
 * En el Hito 2 se suma {@code PrioridadPorIA} sin tocar los servicios.
 */
public interface EstrategiaPrioridad {

    Prioridad calcular(Reclamo reclamo);
}
