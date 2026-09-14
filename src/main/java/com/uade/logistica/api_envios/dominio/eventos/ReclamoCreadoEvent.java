package com.uade.logistica.api_envios.dominio.eventos;

import com.uade.logistica.api_envios.dominio.Reclamo;
import java.time.LocalDateTime;

public class ReclamoCreadoEvent {
    private final Reclamo reclamo;
    private final LocalDateTime fechaOcurrencia;

    public ReclamoCreadoEvent(Reclamo reclamo) {
        this.reclamo = reclamo;
        this.fechaOcurrencia = LocalDateTime.now();
    }

    public Reclamo getReclamo() {
        return reclamo;
    }

    public LocalDateTime getFechaOcurrencia() {
        return fechaOcurrencia;
    }
}
