package com.uade.logistica.api_envios.servicios;

import org.springframework.stereotype.Component;

@Component("ALUMBRADO")
public class AsignacionAlumbradoStrategy implements AsignacionAreaStrategy {
    @Override
    public String determinarArea(String descripcion) {
        return "Dirección de Mantenimiento del Espacio Público y Luminarias";
    }
}