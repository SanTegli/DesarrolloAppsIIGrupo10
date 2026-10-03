package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.persistencia.jpa.AreaMunicipalJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AreaMunicipalRepositoryJpa implements AreaMunicipalRepository {
    private final AreaMunicipalJpaRepository jpa;

    public AreaMunicipalRepositoryJpa(AreaMunicipalJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public AreaMunicipal guardar(AreaMunicipal area) {
        return CargadorRelaciones.cargar(jpa.save(area));
    }

    @Override
    public Optional<AreaMunicipal> buscarPorId(Long id) {
        return jpa.findById(id).map(CargadorRelaciones::cargar);
    }

    @Override
    public List<AreaMunicipal> buscarTodas() {
        return jpa.findAllByOrderByNombreAsc().stream().map(CargadorRelaciones::cargar).toList();
    }

    @Override
    public List<AreaMunicipal> buscarCandidatas(Long categoriaId, Long barrioId) {
        return jpa.buscarCandidatas(categoriaId, barrioId).stream().map(CargadorRelaciones::cargar).toList();
    }
}

