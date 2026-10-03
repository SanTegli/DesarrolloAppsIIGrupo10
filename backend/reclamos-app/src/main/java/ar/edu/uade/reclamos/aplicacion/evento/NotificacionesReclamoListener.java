package ar.edu.uade.reclamos.aplicacion.evento;

import ar.edu.uade.reclamos.aplicacion.servicio.ServicioNotificaciones;
import ar.edu.uade.reclamos.dominio.evento.EstadoReclamoCambiado;
import ar.edu.uade.reclamos.dominio.evento.ReclamoAsignado;
import ar.edu.uade.reclamos.dominio.evento.ReclamoCreado;
import ar.edu.uade.reclamos.dominio.evento.ReclamoResuelto;
import ar.edu.uade.reclamos.dominio.evento.ReclamoVencido;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observador de los eventos de dominio que genera los avisos.
 * Reacciones sincronas: los avisos se guardan en la transaccion del caso de uso, y si fallan
 * se revierte todo. Que aviso corresponde a cada evento lo decide {@link ServicioNotificaciones}.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class NotificacionesReclamoListener {
    private final ServicioNotificaciones notificaciones;

    public NotificacionesReclamoListener(ServicioNotificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    @EventListener
    public void creado(ReclamoCreado evento) {
        notificaciones.avisarIngreso(evento.numeroReclamo(), evento.ocurridoEn());
    }

    @EventListener
    public void asignado(ReclamoAsignado evento) {
        notificaciones.avisarAsignacion(evento.numeroReclamo(), evento.areaId(), evento.areaNombre(),
                evento.ocurridoEn());
    }

    @EventListener
    public void estadoCambiado(EstadoReclamoCambiado evento) {
        notificaciones.avisarCambioDeEstado(evento.numeroReclamo(), evento.estadoAnterior(),
                evento.estadoNuevo(), evento.ocurridoEn());
    }

    @EventListener
    public void resuelto(ReclamoResuelto evento) {
        notificaciones.avisarResolucion(evento.numeroReclamo(), evento.ocurridoEn());
    }

    @EventListener
    public void vencido(ReclamoVencido evento) {
        notificaciones.avisarVencimiento(evento.numeroReclamo(), evento.prioridadNueva(), evento.ocurridoEn());
    }
}
