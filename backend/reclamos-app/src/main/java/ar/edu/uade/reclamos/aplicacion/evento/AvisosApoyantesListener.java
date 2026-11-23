package ar.edu.uade.reclamos.aplicacion.evento;

import ar.edu.uade.reclamos.aplicacion.mensajeria.SalidaDeAvisos;
import ar.edu.uade.reclamos.aplicacion.mensajeria.TipoAviso;
import ar.edu.uade.reclamos.dominio.evento.EstadoReclamoCambiado;
import ar.edu.uade.reclamos.dominio.evento.ReclamoApoyado;
import ar.edu.uade.reclamos.dominio.evento.ReclamoResuelto;
import ar.edu.uade.reclamos.dominio.modelo.CanalNotificacion;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.ApoyoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.NotificacionRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import java.time.LocalDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quinto observador (PR 27): los vecinos que se asociaron a un reclamo ajeno ("Asociarme") reciben sus avisos.
 * Se entera al asociarse, cuando el reclamo cambia de estado y cuando se resuelve. Como los demás avisos, quedan
 * en la bandeja y salen por correo con la mensajería encendida. Se sumó sin tocar {@code ReclamoService} ni
 * {@code NotificacionesReclamoListener}.
 *
 * <p>Los textos no dicen quién creó el reclamo: el apoyo no da acceso a sus datos.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AvisosApoyantesListener {

    private final ApoyoRepository apoyos;
    private final ReclamoRepository reclamos;
    private final UsuarioRepository usuarios;
    private final NotificacionRepository notificaciones;
    private final SalidaDeAvisos salida;

    public AvisosApoyantesListener(ApoyoRepository apoyos, ReclamoRepository reclamos, UsuarioRepository usuarios,
                                   NotificacionRepository notificaciones, SalidaDeAvisos salida) {
        this.apoyos = apoyos;
        this.reclamos = reclamos;
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
        this.salida = salida;
    }

    @EventListener
    public void apoyado(ReclamoApoyado evento) {
        reclamos.buscarPorNumero(evento.numeroReclamo()).ifPresent(reclamo -> {
            notificar(reclamo, reclamo.getCiudadano(),
                    "Un vecino se asoció a tu reclamo " + reclamo.getNumero() + ".",
                    evento.ocurridoEn(), TipoAviso.RECLAMO_APOYADO);
            usuarios.buscarPorId(evento.ciudadanoId()).ifPresent(vecino -> notificar(reclamo, vecino,
                    "Te sumaste al reclamo " + reclamo.getNumero() + ". Te vamos a avisar cada vez que cambie.",
                    evento.ocurridoEn(), TipoAviso.RECLAMO_APOYADO));
        });
    }

    /** La resolución tiene su propio aviso; acá se omite para no duplicarlo. */
    @EventListener
    public void estadoCambiado(EstadoReclamoCambiado evento) {
        if (evento.estadoNuevo() == EstadoReclamo.RESUELTO) {
            return;
        }
        avisarApoyantes(evento.numeroReclamo(), "El reclamo " + evento.numeroReclamo() + " que apoyaste paso de "
                + evento.estadoAnterior() + " a " + evento.estadoNuevo() + ".", evento.ocurridoEn(),
                TipoAviso.ESTADO_CAMBIADO);
    }

    @EventListener
    public void resuelto(ReclamoResuelto evento) {
        avisarApoyantes(evento.numeroReclamo(), "El reclamo " + evento.numeroReclamo()
                + " que apoyaste fue resuelto.", evento.ocurridoEn(), TipoAviso.RECLAMO_RESUELTO);
    }

    private void avisarApoyantes(String numero, String mensaje, LocalDateTime fecha, TipoAviso tipo) {
        reclamos.buscarPorNumero(numero).ifPresent(reclamo -> apoyos.buscarApoyantes(reclamo.getId())
                .forEach(vecino -> notificar(reclamo, vecino, mensaje, fecha, tipo)));
    }

    private void notificar(Reclamo reclamo, Usuario destinatario, String mensaje, LocalDateTime fecha,
                           TipoAviso tipo) {
        Notificacion aviso = notificaciones.guardar(
                new Notificacion(reclamo, destinatario, CanalNotificacion.INTERNO, mensaje, fecha));
        salida.enviar(aviso, tipo);
    }
}
