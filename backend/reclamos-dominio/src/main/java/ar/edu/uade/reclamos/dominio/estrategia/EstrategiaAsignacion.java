package ar.edu.uade.reclamos.dominio.estrategia;

import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.util.List;
import java.util.Optional;

/**
 * Patrón Strategy: algoritmo intercambiable para elegir el área responsable de un reclamo.
 *
 * <p>Implementaciones: {@code AsignacionPorJurisdiccion} y {@code AsignacionPorCargaDeTrabajo}.
 * Las áreas candidatas las entrega {@code CatalogoCobertura}; en la Segunda Parte salen del servicio
 * SOAP de cobertura y esta interfaz no cambia.
 */
public interface EstrategiaAsignacion {

    /**
     * @param candidatas áreas activas que atienden la categoría del reclamo y tienen jurisdicción sobre
     *                   su barrio (resultado de {@code AreaMunicipalRepository.buscarCandidatas})
     * @return el área elegida, o vacío si ninguna puede atenderlo; en ese caso el reclamo queda INGRESADO
     */
    Optional<AreaMunicipal> seleccionarArea(Reclamo reclamo, List<AreaMunicipal> candidatas);
}
