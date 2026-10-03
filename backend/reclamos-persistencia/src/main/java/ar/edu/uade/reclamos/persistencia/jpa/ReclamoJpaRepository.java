package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface ReclamoJpaRepository extends JpaRepository<Reclamo, Long> {
    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    Optional<Reclamo> findByNumero(String numero);

    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    List<Reclamo> findAllByOrderByFechaCreacionDescIdDesc();

    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    List<Reclamo> findByCiudadanoIdOrderByFechaCreacionDescIdDesc(Long ciudadanoId);

    @EntityGraph(attributePaths = {"ciudadano", "categoria", "barrio.municipio", "area", "agente"})
    List<Reclamo> findByAreaIdOrderByFechaCreacionDescIdDesc(Long areaId);

    long countByAreaIdAndEstadoIn(Long areaId, Collection<EstadoReclamo> estados);

    List<Reclamo> findByVencidoFalseAndEstadoInAndFechaLimiteBeforeOrderByFechaCreacionDescIdDesc(
            Collection<EstadoReclamo> estados, LocalDateTime ahora);
}

