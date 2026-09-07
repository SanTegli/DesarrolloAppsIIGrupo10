package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dominio.Reclamo;

public interface ReclamoFactory {
    Reclamo crearReclamo(String dni, String direccion, String descripcion, String categoria);
}