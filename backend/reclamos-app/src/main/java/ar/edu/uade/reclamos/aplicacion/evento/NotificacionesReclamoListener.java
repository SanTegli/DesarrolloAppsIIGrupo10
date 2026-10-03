package ar.edu.uade.reclamos.aplicacion.evento;

import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.evento.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.NotificacionRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.LocalDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reacciones sincronas: los avisos se guardan en la transaccion del caso de uso. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class NotificacionesReclamoListener {
    private final ReclamoRepository reclamos;
    private final NotificacionRepository notificaciones;

    public NotificacionesReclamoListener(ReclamoRepository reclamos, NotificacionRepository notificaciones) {
        this.reclamos = reclamos;
        this.notificaciones = notificaciones;
    }

    @EventListener
    public void creado(ReclamoCreado evento) {
        Reclamo reclamo = reclamo(evento);
        notificar(reclamo, reclamo.getCiudadano(), "Tu reclamo " + evento.numeroReclamo()
                + " fue ingresado.", evento.ocurridoEn());
    }

    @EventListener
    public void asignado(ReclamoAsignado evento) {
        Reclamo reclamo = reclamo(evento);
        notificar(reclamo, reclamo.getCiudadano(), "Tu reclamo " + evento.numeroReclamo()
                + " fue asignado al area " + evento.areaNombre() + ".", evento.ocurridoEn());
    }

    @EventListener
    public void estadoCambiado(EstadoReclamoCambiado evento) {
        // La resolucion tiene su propio evento y genera un solo aviso al ciudadano.
        if (evento.estadoNuevo() == EstadoReclamo.RESUELTO) {
            return;
        }
        Reclamo reclamo = reclamo(evento);
        String mensaje = "El reclamo " + evento.numeroReclamo() + " paso de "
                + evento.estadoAnterior() + " a " + evento.estadoNuevo() + ".";
        notificar(reclamo, reclamo.getCiudadano(), mensaje, evento.ocurridoEn());
        // Reapertura y cierre son decisiones del ciudadano relevantes para el agente a cargo.
        if (evento.estadoAnterior() == EstadoReclamo.RESUELTO && reclamo.getAgente() != null) {
            notificar(reclamo, reclamo.getAgente(), mensaje, evento.ocurridoEn());
        }
    }

    @EventListener
    public void resuelto(ReclamoResuelto evento) {
        Reclamo reclamo = reclamo(evento);
        notificar(reclamo, reclamo.getCiudadano(), "Tu reclamo " + evento.numeroReclamo()
                + " fue resuelto. Podes confirmar el cierre o reabrirlo.", evento.ocurridoEn());
    }

    private Reclamo reclamo(EventoDominio evento) {
        return reclamos.buscarPorNumero(evento.numeroReclamo())
                .orElseThrow(() -> new RecursoNoEncontradoException("reclamo", evento.numeroReclamo()));
    }

    private void notificar(Reclamo reclamo, Usuario destinatario, String mensaje, LocalDateTime fecha) {
        notificaciones.guardar(new Notificacion(reclamo, destinatario, CanalNotificacion.INTERNO, mensaje, fecha));
    }
}
