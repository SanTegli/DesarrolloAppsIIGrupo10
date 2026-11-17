package ar.edu.uade.reclamos.aplicacion.evento;

import ar.edu.uade.reclamos.aplicacion.prioridad.SolicitudesReevaluacion;
import ar.edu.uade.reclamos.dominio.evento.ReclamoCreado;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tercer observador del alta: si la IA no respondió a tiempo y el reclamo quedó con la prioridad pendiente,
 * pide que la IA lo revise después. Corre dentro de la transacción del alta, así el pedido (en la outbox) se
 * guarda junto con el reclamo o no se guarda.
 *
 * <p>{@code ReclamoService} no se enteró de nada: el Observer permite sumar esta reacción sin tocarlo.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ReevaluacionPrioridadListener {

    private final ReclamoRepository reclamos;
    private final SolicitudesReevaluacion solicitudes;

    public ReevaluacionPrioridadListener(ReclamoRepository reclamos, SolicitudesReevaluacion solicitudes) {
        this.reclamos = reclamos;
        this.solicitudes = solicitudes;
    }

    @EventListener
    public void creado(ReclamoCreado evento) {
        reclamos.buscarPorNumero(evento.numeroReclamo())
                .filter(reclamo -> reclamo.estaPendienteDeIA())
                .ifPresent(reclamo -> solicitudes.solicitar(reclamo.getNumero()));
    }
}
