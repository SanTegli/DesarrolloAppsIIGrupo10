package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dominio.ReclamoEvento;
import com.uade.logistica.api_envios.dominio.eventos.ReclamoCreadoEvent;
import com.uade.logistica.api_envios.dominio.eventos.ReclamoResueltoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final List<ReclamoObserver> observadores;
    private final List<ReclamoEvento> historialEventos = Collections.synchronizedList(new ArrayList<>());

    public NotificacionService(List<ReclamoObserver> observadores) {
        this.observadores = observadores;
    }

    @EventListener
    public void manejarReclamoCreado(ReclamoCreadoEvent event) {
        String detalle = "Reclamo asignado al área: " + event.getReclamo().getAreaMunicipal()
                + " con prioridad " + event.getReclamo().getPrioridad();
        ReclamoEvento evento = new ReclamoEvento(event.getReclamo().getId(), "ReclamoCreado", detalle);

        historialEventos.add(evento);
        log.info("[EVENTO DE DOMINIO RECIBIDO] Tipo: {} | Reclamo: {}", evento.getTipoEvento(), evento.getReclamoId());

        notificarObservadores(evento);
    }

    @EventListener
    public void manejarReclamoResuelto(ReclamoResueltoEvent event) {
        String detalle = "Reclamo solucionado con éxito para ciudadano DNI: " + event.getReclamo().getDniCiudadano();
        ReclamoEvento evento = new ReclamoEvento(event.getReclamo().getId(), "ReclamoResuelto", detalle);

        historialEventos.add(evento);
        log.info("[EVENTO DE DOMINIO RECIBIDO] Tipo: {} | Reclamo: {}", evento.getTipoEvento(), evento.getReclamoId());

        notificarObservadores(evento);
    }

    private void notificarObservadores(ReclamoEvento evento) {
        for (ReclamoObserver obs : observadores) {
            try {
                obs.notificar(evento);
            } catch (Exception e) {
                log.error("Error al notificar observador {}: {}", obs.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    public List<ReclamoEvento> obtenerHistorialEventos() {
        return new ArrayList<>(historialEventos);
    }
}
