package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dominio.Reclamo;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component("URGENTE_FACTORY")
public class ReclamoUrgenteFactory implements ReclamoFactory {
    @Override
    public Reclamo crearReclamo(String dni, String direccion, String descripcion, String categoria) {
        String reclamoId = "REC-URG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Reclamo reclamo = new Reclamo(reclamoId, dni, direccion, descripcion, categoria);
        reclamo.setPrioridad("ALTA");
        return reclamo;
    }
}