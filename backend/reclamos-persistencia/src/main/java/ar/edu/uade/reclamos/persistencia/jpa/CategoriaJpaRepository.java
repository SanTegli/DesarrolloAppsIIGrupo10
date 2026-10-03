package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface CategoriaJpaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findAllByOrderByNombreAsc();
}

