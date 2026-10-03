package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import java.util.List;
import java.util.Optional;

public interface MunicipioRepository {

    Municipio guardar(Municipio municipio);

    Optional<Municipio> buscarPorId(Long id);

    List<Municipio> buscarTodos();
}
