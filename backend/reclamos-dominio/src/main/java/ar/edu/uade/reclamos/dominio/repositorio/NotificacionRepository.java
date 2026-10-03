package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import java.util.List;

public interface NotificacionRepository {

    Notificacion guardar(Notificacion notificacion);

    /** Notificaciones del reclamo, de la más vieja a la más nueva. */
    List<Notificacion> buscarPorReclamo(String numeroReclamo);

    /** Notificaciones recibidas por el usuario, de la más nueva a la más vieja. */
    List<Notificacion> buscarPorDestinatario(Long usuarioId);
}
