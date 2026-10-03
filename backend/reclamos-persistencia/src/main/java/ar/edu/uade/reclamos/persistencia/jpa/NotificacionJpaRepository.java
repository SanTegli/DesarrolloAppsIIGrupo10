package ar.edu.uade.reclamos.persistencia.jpa;

import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Contrato técnico interno; los servicios usan el Repository del dominio. */
public interface NotificacionJpaRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByReclamoNumeroOrderByFechaEnvioAscIdAsc(String numero);
    List<Notificacion> findByDestinatarioIdOrderByFechaEnvioDescIdDesc(Long usuarioId);
}

