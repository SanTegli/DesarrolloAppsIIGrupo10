package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.Apoyo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface ApoyoJpaRepository extends JpaRepository<Apoyo, Long> {
    Optional<Apoyo> findByCiudadanoIdAndReclamoId(Long ciudadanoId, Long reclamoId);

    long countByReclamoId(Long reclamoId);
}
