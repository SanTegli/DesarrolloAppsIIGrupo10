package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.modelo.CanalNotificacion;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.NotificacionRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import ar.edu.uade.reclamos.aplicacion.mensajeria.SalidaDeAvisos;
import ar.edu.uade.reclamos.aplicacion.mensajeria.TipoAviso;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Decide a quién se avisa y con qué texto, y guarda cada aviso como {@link Notificacion} con canal INTERNO.
 * Además, cada aviso sale por {@link SalidaDeAvisos}: con la mensajería habilitada, va a la outbox y de ahí a
 * RabbitMQ, donde {@code servicio-notificaciones} lo manda por correo.
 * No abre transacciones: lo llama el listener, dentro de la transacción del caso de uso.
 */
@Service
public class ServicioNotificaciones {
    private final ReclamoRepository reclamos;
    private final NotificacionRepository notificaciones;
    private final UsuarioRepository usuarios;
    private final SalidaDeAvisos salida;

    /** Sin mensajería: los avisos quedan solo internos. */
    public ServicioNotificaciones(ReclamoRepository reclamos, NotificacionRepository notificaciones,
                                  UsuarioRepository usuarios) {
        this(reclamos, notificaciones, usuarios, SalidaDeAvisos.NINGUNA);
    }

    @Autowired
    public ServicioNotificaciones(ReclamoRepository reclamos, NotificacionRepository notificaciones,
                                  UsuarioRepository usuarios, SalidaDeAvisos salida) {
        this.reclamos = reclamos;
        this.notificaciones = notificaciones;
        this.usuarios = usuarios;
        this.salida = salida;
    }

    public void avisarIngreso(String numero, LocalDateTime fecha) {
        Reclamo reclamo = reclamo(numero);
        notificar(reclamo, reclamo.getCiudadano(), "Tu reclamo " + numero + " fue ingresado.", fecha,
                TipoAviso.RECLAMO_CREADO);
    }

    /** Avisa al ciudadano y a los agentes activos del área que recibe el reclamo. */
    public void avisarAsignacion(String numero, Long areaId, String areaNombre, LocalDateTime fecha) {
        Reclamo reclamo = reclamo(numero);
        notificar(reclamo, reclamo.getCiudadano(),
                "Tu reclamo " + numero + " fue asignado al area " + areaNombre + ".", fecha,
                TipoAviso.RECLAMO_ASIGNADO);
        String aviso = "El reclamo " + numero + " fue asignado a tu area " + areaNombre + ".";
        usuarios.buscarAgentesPorArea(areaId)
                .forEach(agente -> notificar(reclamo, agente, aviso, fecha, TipoAviso.RECLAMO_ASIGNADO));
    }

    /** La resolución tiene su propio aviso ({@link #avisarResolucion}); acá se omite para no duplicarlo. */
    public void avisarCambioDeEstado(String numero, EstadoReclamo anterior, EstadoReclamo nuevo,
                                     LocalDateTime fecha) {
        if (nuevo == EstadoReclamo.RESUELTO) {
            return;
        }
        Reclamo reclamo = reclamo(numero);
        String mensaje = "El reclamo " + numero + " paso de " + anterior + " a " + nuevo + ".";
        notificar(reclamo, reclamo.getCiudadano(), mensaje, fecha, TipoAviso.ESTADO_CAMBIADO);
        // Reapertura y cierre son decisiones del ciudadano relevantes para el agente a cargo.
        if (anterior == EstadoReclamo.RESUELTO && reclamo.getAgente() != null) {
            notificar(reclamo, reclamo.getAgente(), mensaje, fecha, TipoAviso.ESTADO_CAMBIADO);
        }
    }

    public void avisarResolucion(String numero, LocalDateTime fecha) {
        Reclamo reclamo = reclamo(numero);
        notificar(reclamo, reclamo.getCiudadano(),
                "Tu reclamo " + numero + " fue resuelto. Podes confirmar el cierre o reabrirlo.", fecha,
                TipoAviso.RECLAMO_RESUELTO);
    }

    /** Avisa al ciudadano y a quien debe actuar: el agente a cargo o, si nadie lo tomó, los agentes del área. */
    public void avisarVencimiento(String numero, Prioridad prioridadNueva, LocalDateTime fecha) {
        Reclamo reclamo = reclamo(numero);
        notificar(reclamo, reclamo.getCiudadano(), "Tu reclamo " + numero
                + " supero su fecha limite y paso a prioridad " + prioridadNueva + ".", fecha,
                TipoAviso.RECLAMO_VENCIDO);
        String aviso = "El reclamo " + numero + " vencio sin resolverse. Prioridad " + prioridadNueva + ".";
        if (reclamo.getAgente() != null) {
            notificar(reclamo, reclamo.getAgente(), aviso, fecha, TipoAviso.RECLAMO_VENCIDO);
        } else if (reclamo.getArea() != null) {
            usuarios.buscarAgentesPorArea(reclamo.getArea().getId())
                    .forEach(agente -> notificar(reclamo, agente, aviso, fecha, TipoAviso.RECLAMO_VENCIDO));
        }
    }

    private Reclamo reclamo(String numero) {
        return reclamos.buscarPorNumero(numero)
                .orElseThrow(() -> new RecursoNoEncontradoException("reclamo", numero));
    }

    private void notificar(Reclamo reclamo, Usuario destinatario, String mensaje, LocalDateTime fecha,
                           TipoAviso tipo) {
        Notificacion aviso = notificaciones.guardar(
                new Notificacion(reclamo, destinatario, CanalNotificacion.INTERNO, mensaje, fecha));
        salida.enviar(aviso, tipo);
    }
}
