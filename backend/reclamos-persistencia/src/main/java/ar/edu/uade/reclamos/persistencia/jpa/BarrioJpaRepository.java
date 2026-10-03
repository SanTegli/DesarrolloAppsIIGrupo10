package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface BarrioJpaRepository extends JpaRepository<Barrio, Long> {
    @EntityGraph(attributePaths = "municipio")
    List<Barrio> findAllByOrderByNombreAsc();

    @EntityGraph(attributePaths = "municipio")
    List<Barrio> findByMunicipioIdOrderByNombreAsc(Long municipioId);
}

