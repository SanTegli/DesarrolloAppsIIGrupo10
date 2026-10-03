package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.repositorio.BarrioRepository;
import ar.edu.uade.reclamos.persistencia.jpa.BarrioJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class BarrioRepositoryJpa implements BarrioRepository {
    private final BarrioJpaRepository jpa;

    public BarrioRepositoryJpa(BarrioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Barrio guardar(Barrio barrio) {
        return jpa.save(barrio);
    }

    @Override
    public Optional<Barrio> buscarPorId(Long id) {
        return jpa.findById(id).map(CargadorRelaciones::cargar);
    }

    @Override
    public List<Barrio> buscarTodos() {
        return jpa.findAllByOrderByNombreAsc();
    }

    @Override
    public List<Barrio> buscarPorMunicipio(Long municipioId) {
        return jpa.findByMunicipioIdOrderByNombreAsc(municipioId);
    }
}

