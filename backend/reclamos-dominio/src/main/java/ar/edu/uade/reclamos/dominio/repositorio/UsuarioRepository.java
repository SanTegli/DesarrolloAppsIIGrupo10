package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import java.util.List;
import java.util.Optional;

/** Repository de usuarios de los tres roles. */
public interface UsuarioRepository {

    Usuario guardar(Usuario usuario);

    Optional<Usuario> buscarPorId(Long id);

    Optional<Usuario> buscarPorDni(String dni);

    List<Usuario> buscarTodos();

    /** Agentes activos del área. Se usan para avisarles cuando entra un reclamo. */
    List<AgenteMunicipal> buscarAgentesPorArea(Long areaId);
}
