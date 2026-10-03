package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import java.util.List;
import java.util.Optional;

public interface AreaMunicipalRepository {

    /** Guarda el área con sus relaciones a barrios y categorías. */
    AreaMunicipal guardar(AreaMunicipal area);

    Optional<AreaMunicipal> buscarPorId(Long id);

    /** Todas las áreas ordenadas por nombre. */
    List<AreaMunicipal> buscarTodas();

    /**
     * Áreas activas que atienden la categoría y tienen jurisdicción sobre el barrio, ordenadas por id.
     * Es la entrada de {@code EstrategiaAsignacion}.
     */
    List<AreaMunicipal> buscarCandidatas(Long categoriaId, Long barrioId);
}
