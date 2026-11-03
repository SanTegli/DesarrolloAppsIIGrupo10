package ar.edu.uade.reclamos.dominio.repositorio;

import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import java.util.List;
import java.util.Optional;

/** La outbox: mensajes que tienen que salir por el broker. */
public interface MensajeSalienteRepository {

    MensajeSaliente guardar(MensajeSaliente mensaje);

    /** Los pendientes más viejos primero, como máximo {@code limite}. */
    List<MensajeSaliente> buscarPendientes(int limite);

    Optional<MensajeSaliente> buscarPorEventId(String eventId);

    long contarPorEstado(EstadoMensaje estado);
}
