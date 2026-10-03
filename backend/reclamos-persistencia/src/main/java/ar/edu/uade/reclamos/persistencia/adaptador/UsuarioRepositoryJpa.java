package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import ar.edu.uade.reclamos.persistencia.jpa.UsuarioJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class UsuarioRepositoryJpa implements UsuarioRepository {
    private final UsuarioJpaRepository jpa;

    public UsuarioRepositoryJpa(UsuarioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Usuario guardar(Usuario usuario) {
        return CargadorRelaciones.cargar(jpa.save(usuario));
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return jpa.findById(id).map(CargadorRelaciones::cargar);
    }

    @Override
    public Optional<Usuario> buscarPorDni(String dni) {
        return jpa.findByDni(dni).map(CargadorRelaciones::cargar);
    }

    @Override
    public List<Usuario> buscarTodos() {
        return jpa.findAllByOrderByIdAsc().stream().map(CargadorRelaciones::cargar).toList();
    }

    @Override
    public List<AgenteMunicipal> buscarAgentesPorArea(Long areaId) {
        List<AgenteMunicipal> agentes = jpa.buscarAgentesPorArea(areaId);
        agentes.forEach(CargadorRelaciones::cargar);
        return agentes;
    }
}
