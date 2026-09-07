package com.uade.logistica.api_envios.servicios;

import org.springframework.stereotype.Component;

@Component("TRANSITO")
public class AsignacionTransitoStrategy implements AsignacionAreaStrategy {
    @Override
    public String determinarArea(String descripcion) {
        return "Dirección General de Seguridad Vial y Semaforización";
    }
}