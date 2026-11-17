package ar.edu.uade.reclamos.aplicacion.evento;

import ar.edu.uade.reclamos.aplicacion.categoria.SolicitudesSugerenciaCategoria;
import ar.edu.uade.reclamos.dominio.evento.ReclamoCreado;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuarto observador del alta (PR 26): si el vecino eligió "Otro problema", pide que la IA sugiera la categoría.
 * Corre dentro de la transacción del alta, así el pedido (en la outbox) se guarda junto con el reclamo.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class SugerenciaCategoriaListener {

    private final SolicitudesSugerenciaCategoria solicitudes;

    public SugerenciaCategoriaListener(SolicitudesSugerenciaCategoria solicitudes) {
        this.solicitudes = solicitudes;
    }

    @EventListener
    public void creado(ReclamoCreado evento) {
        if (evento.categoriaOtra()) {
            solicitudes.solicitar(evento.numeroReclamo());
        }
    }
}
