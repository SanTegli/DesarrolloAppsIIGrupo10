package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.dominio.evento.PublicadorEventos;
import ar.edu.uade.reclamos.dominio.evento.ReclamoVencido;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controla el plazo de atención (SLA). Un reclamo que pasó su fecha límite sin resolverse se marca
 * como vencido, sube un nivel de prioridad y genera el evento {@link ReclamoVencido}.
 */
@Service
public class ServicioVencimientos {
    private final ReclamoRepository reclamos;
    private final PublicadorEventos eventos;
    private final Clock reloj;

    public ServicioVencimientos(ReclamoRepository reclamos, PublicadorEventos eventos, Clock reloj) {
        this.reclamos = reclamos;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * Marca todos los reclamos vencidos todavía sin marcar. Cada reclamo se marca una sola vez,
     * así que ejecutarlo de nuevo no repite eventos.
     *
     * @return cantidad de reclamos marcados en esta ejecución
     */
    @Transactional
    public int marcarVencidos() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        int marcados = 0;
        for (Reclamo reclamo : reclamos.buscarVencidosSinMarcar(ahora)) {
            if (reclamo.marcarVencido(ahora)) {
                Reclamo guardado = reclamos.guardar(reclamo);
                eventos.publicar(new ReclamoVencido(guardado.getNumero(),
                        guardado.getArea() == null ? null : guardado.getArea().getId(),
                        guardado.getPrioridad(), guardado.getFechaLimite(), ahora));
                marcados++;
            }
        }
        return marcados;
    }
}
