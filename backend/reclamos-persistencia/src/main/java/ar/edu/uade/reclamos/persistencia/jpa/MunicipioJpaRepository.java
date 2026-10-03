package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface MunicipioJpaRepository extends JpaRepository<Municipio, Long> {
    List<Municipio> findAllByOrderByIdAsc();
}

