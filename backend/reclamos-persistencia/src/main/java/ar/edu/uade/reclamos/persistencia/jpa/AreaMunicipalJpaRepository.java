package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface AreaMunicipalJpaRepository extends JpaRepository<AreaMunicipal, Long> {
    @EntityGraph(attributePaths = "municipio")
    List<AreaMunicipal> findAllByOrderByNombreAsc();

    @EntityGraph(attributePaths = "municipio")
    @Query("select distinct a from AreaMunicipal a join a.categorias c join a.barrios b "
            + "where a.activa = true and c.id = :categoriaId and b.id = :barrioId order by a.id")
    List<AreaMunicipal> buscarCandidatas(@Param("categoriaId") Long categoriaId,
                                       @Param("barrioId") Long barrioId);
}

