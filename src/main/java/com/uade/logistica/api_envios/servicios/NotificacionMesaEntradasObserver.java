package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dominio.ReclamoEvento;
import org.springframework.stereotype.Component;

@Component
public class NotificacionMesaEntradasObserver implements ReclamoObserver {
    @Override
    public void notificar(ReclamoEvento evento) {
        System.out.println("[MESA DE ENTRADAS - OBSERVER] Notificación enviada -> Evento: " 
                + evento.getTipoEvento() 
                + " | Reclamo: " + evento.getReclamoId() 
                + " | Detalle: " + evento.getDetalle());
    }
}