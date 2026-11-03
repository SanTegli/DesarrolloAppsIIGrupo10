package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultaCatalogosService {
    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final BarrioRepository barrios;
    private final AreaMunicipalRepository areas;

    public ConsultaCatalogosService(UsuarioRepository usuarios, CategoriaRepository categorias,
                                     BarrioRepository barrios, AreaMunicipalRepository areas) {
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.barrios = barrios;
        this.areas = areas;
    }

    /** El listado completo de usuarios solo lo ve un administrador. */
    public List<Usuario> usuarios(Long actorId) {
        if (!(usuario(actorId) instanceof Administrador)) {
            throw new AccesoDenegadoException("Solo un administrador puede ver la lista de usuarios.");
        }
        return usuarios.buscarTodos();
    }

    /** Un usuario se puede consultar a sí mismo; a los demás, solo un administrador. */
    public Usuario usuario(Long actorId, Long id) {
        if (!actorId.equals(id) && !(usuario(actorId) instanceof Administrador)) {
            throw new AccesoDenegadoException("Solo un administrador puede ver los datos de otro usuario.");
        }
        return usuario(id);
    }

    public Usuario usuario(Long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("usuario", id));
    }

    public List<Categoria> categorias() {
        return categorias.buscarTodas();
    }

    public List<Barrio> barrios() {
        return barrios.buscarTodos();
    }

    public List<AreaMunicipal> areas() {
        return areas.buscarTodas();
    }
}
