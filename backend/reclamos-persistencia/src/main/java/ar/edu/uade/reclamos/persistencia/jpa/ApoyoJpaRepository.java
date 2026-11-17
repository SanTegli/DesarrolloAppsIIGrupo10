package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.Apoyo;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface ApoyoJpaRepository extends JpaRepository<Apoyo, Long> {
    Optional<Apoyo> findByCiudadanoIdAndReclamoId(Long ciudadanoId, Long reclamoId);

    long countByReclamoId(Long reclamoId);

    @Query("select a.ciudadano from Apoyo a where a.reclamo.id = :reclamoId order by a.fecha, a.id")
    List<Ciudadano> buscarApoyantes(@Param("reclamoId") Long reclamoId);
}
