package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import java.util.List;
import java.util.Optional;

public interface BarrioRepository {

    Barrio guardar(Barrio barrio);

    Optional<Barrio> buscarPorId(Long id);

    /** Todos los barrios ordenados por nombre. */
    List<Barrio> buscarTodos();

    List<Barrio> buscarPorMunicipio(Long municipioId);
}
