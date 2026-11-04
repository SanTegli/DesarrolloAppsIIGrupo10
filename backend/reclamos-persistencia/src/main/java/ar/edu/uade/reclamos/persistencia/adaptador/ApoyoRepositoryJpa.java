package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.Apoyo;
import ar.edu.uade.reclamos.dominio.repositorio.ApoyoRepository;
import ar.edu.uade.reclamos.persistencia.jpa.ApoyoJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ApoyoRepositoryJpa implements ApoyoRepository {
    private final ApoyoJpaRepository jpa;

    public ApoyoRepositoryJpa(ApoyoJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** Con flush: si el apoyo ya existía, la restricción única falla acá y no al confirmar la transacción. */
    @Override
    @Transactional
    public Apoyo guardar(Apoyo apoyo) {
        return jpa.saveAndFlush(apoyo);
    }

    @Override
    public Optional<Apoyo> buscar(Long ciudadanoId, Long reclamoId) {
        return jpa.findByCiudadanoIdAndReclamoId(ciudadanoId, reclamoId);
    }

    @Override
    @Transactional
    public void eliminar(Apoyo apoyo) {
        jpa.delete(apoyo);
    }

    @Override
    public long contarPorReclamo(Long reclamoId) {
        return jpa.countByReclamoId(reclamoId);
    }
}
