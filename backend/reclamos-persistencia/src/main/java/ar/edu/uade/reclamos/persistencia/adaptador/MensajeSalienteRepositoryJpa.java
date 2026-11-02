package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import ar.edu.uade.reclamos.persistencia.jpa.MensajeSalienteJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MensajeSalienteRepositoryJpa implements MensajeSalienteRepository {
    private final MensajeSalienteJpaRepository jpa;

    public MensajeSalienteRepositoryJpa(MensajeSalienteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public MensajeSaliente guardar(MensajeSaliente mensaje) {
        return jpa.save(mensaje);
    }

    @Override
    public List<MensajeSaliente> buscarPendientes(int limite) {
        return jpa.findByEstadoOrderByIdAsc(EstadoMensaje.PENDIENTE, PageRequest.of(0, limite));
    }

    @Override
    public Optional<MensajeSaliente> buscarPorEventId(String eventId) {
        return jpa.findByEventId(eventId);
    }

    @Override
    public long contarPorEstado(EstadoMensaje estado) {
        return jpa.countByEstado(estado);
    }
}
