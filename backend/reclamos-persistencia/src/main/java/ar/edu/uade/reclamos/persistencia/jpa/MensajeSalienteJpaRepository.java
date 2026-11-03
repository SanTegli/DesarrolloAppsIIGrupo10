package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface MensajeSalienteJpaRepository extends JpaRepository<MensajeSaliente, Long> {
    List<MensajeSaliente> findByEstadoOrderByIdAsc(EstadoMensaje estado, Pageable pagina);
    Optional<MensajeSaliente> findByEventId(String eventId);
    long countByEstado(EstadoMensaje estado);
}
