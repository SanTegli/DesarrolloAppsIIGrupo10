package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Patrón Repository: el dominio y los servicios piden reclamos por esta interfaz y no conocen JPA.
 * La implementa un adaptador en {@code reclamos-persistencia} (PR 2).
 */
public interface ReclamoRepository {

    /** Inserta o actualiza el reclamo junto con su historial. */
    Reclamo guardar(Reclamo reclamo);

    Optional<Reclamo> buscarPorNumero(String numero);

    /** Todos los reclamos, del más nuevo al más viejo. */
    List<Reclamo> buscarTodos();

    /** Reclamos presentados por el ciudadano, del más nuevo al más viejo. */
    List<Reclamo> buscarPorCiudadano(Long ciudadanoId);

    /** Reclamos asignados al área, del más nuevo al más viejo. */
    List<Reclamo> buscarPorArea(Long areaId);

    /** Reclamos del área en estado ASIGNADO o EN_PROCESO. Lo usa la asignación por carga de trabajo. */
    long contarPendientesPorArea(Long areaId);

    /**
     * Reclamos sin marcar como vencidos, en estado INGRESADO, ASIGNADO o EN_PROCESO,
     * cuya fecha límite es anterior a {@code ahora}.
     */
    List<Reclamo> buscarVencidosSinMarcar(LocalDateTime ahora);
}
