package com.uade.logistica.api_envios.dominio.eventos;

import com.uade.logistica.api_envios.dominio.Reclamo;
import java.time.LocalDateTime;

public class ReclamoResueltoEvent {
    private final Reclamo reclamo;
    private final LocalDateTime fechaResolucion;

    public ReclamoResueltoEvent(Reclamo reclamo) {
        this.reclamo = reclamo;
        this.fechaResolucion = LocalDateTime.now();
    }

    public Reclamo getReclamo() {
        return reclamo;
    }

    public LocalDateTime getFechaResolucion() {
        return fechaResolucion;
    }
}
