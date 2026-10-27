package ar.edu.uade.reclamos.dominio.estrategia;

import ar.edu.uade.reclamos.dominio.modelo.Reclamo;

/**
 * Patrón Strategy: algoritmo intercambiable para calcular la prioridad de un reclamo recién creado.
 *
 * <p>Implementaciones: {@code PrioridadPorCategoria} y {@code PrioridadPorPalabrasClave}. En la Segunda
 * Parte se suma {@code PrioridadPorIA} sin tocar los servicios.
 */
public interface EstrategiaPrioridad {

    /** Calcula la prioridad sin modificar el reclamo; quien la usa la aplica con {@code definirPrioridad}. */
    PrioridadCalculada calcular(Reclamo reclamo);
}
