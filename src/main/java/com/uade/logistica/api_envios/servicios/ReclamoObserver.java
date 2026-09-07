package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dominio.ReclamoEvento;

public interface ReclamoObserver {
    void notificar(ReclamoEvento evento);
}