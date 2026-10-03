package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.persistencia.jpa.CategoriaJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CategoriaRepositoryJpa implements CategoriaRepository {
    private final CategoriaJpaRepository jpa;

    public CategoriaRepositoryJpa(CategoriaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Categoria guardar(Categoria categoria) {
        return jpa.save(categoria);
    }

    @Override
    public Optional<Categoria> buscarPorId(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<Categoria> buscarTodas() {
        return jpa.findAllByOrderByNombreAsc();
    }
}

