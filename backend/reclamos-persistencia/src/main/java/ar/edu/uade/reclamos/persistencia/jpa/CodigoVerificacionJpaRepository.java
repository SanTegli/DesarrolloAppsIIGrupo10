package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.CodigoVerificacion;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface CodigoVerificacionJpaRepository extends JpaRepository<CodigoVerificacion, Long> {

    @EntityGraph(attributePaths = "ciudadano")
    Optional<CodigoVerificacion> findByCodigo(String codigo);

    long countByCiudadanoIdAndFechaCreacionGreaterThanEqual(Long ciudadanoId, LocalDateTime desde);
}
