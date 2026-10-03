package ar.edu.uade.reclamos.persistencia.adaptador;

import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.repositorio.NotificacionRepository;
import ar.edu.uade.reclamos.persistencia.jpa.NotificacionJpaRepository;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class NotificacionRepositoryJpa implements NotificacionRepository {
    private final NotificacionJpaRepository jpa;

    public NotificacionRepositoryJpa(NotificacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Notificacion guardar(Notificacion notificacion) {
        return CargadorRelaciones.cargar(jpa.save(notificacion));
    }

    @Override
    public List<Notificacion> buscarPorReclamo(String numeroReclamo) {
        return jpa.findByReclamoNumeroOrderByFechaEnvioAscIdAsc(numeroReclamo)
                .stream().map(CargadorRelaciones::cargar).toList();
    }

    @Override
    public List<Notificacion> buscarPorDestinatario(Long usuarioId) {
        return jpa.findByDestinatarioIdOrderByFechaEnvioDescIdDesc(usuarioId)
                .stream().map(CargadorRelaciones::cargar).toList();
    }
}

