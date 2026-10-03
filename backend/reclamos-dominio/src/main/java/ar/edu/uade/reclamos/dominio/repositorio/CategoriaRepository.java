package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import java.util.List;
import java.util.Optional;

public interface CategoriaRepository {

    Categoria guardar(Categoria categoria);

    Optional<Categoria> buscarPorId(Long id);

    /** Todas las categorías ordenadas por nombre. */
    List<Categoria> buscarTodas();
}
