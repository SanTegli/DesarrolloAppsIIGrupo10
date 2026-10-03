package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.repositorio.MunicipioRepository;
import ar.edu.uade.reclamos.persistencia.jpa.MunicipioJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MunicipioRepositoryJpa implements MunicipioRepository {
    private final MunicipioJpaRepository jpa;

    public MunicipioRepositoryJpa(MunicipioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Municipio guardar(Municipio municipio) {
        return jpa.save(municipio);
    }

    @Override
    public Optional<Municipio> buscarPorId(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<Municipio> buscarTodos() {
        return jpa.findAllByOrderByIdAsc();
    }
}

