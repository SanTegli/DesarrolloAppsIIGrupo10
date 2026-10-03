package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface UsuarioJpaRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByDni(String dni);
    List<Usuario> findAllByOrderByIdAsc();

    @Query("select a from AgenteMunicipal a where a.activo = true and a.area.id = :areaId order by a.id")
    List<AgenteMunicipal> buscarAgentesPorArea(@Param("areaId") Long areaId);
}

