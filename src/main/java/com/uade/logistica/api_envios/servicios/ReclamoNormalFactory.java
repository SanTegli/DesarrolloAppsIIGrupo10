package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dominio.Reclamo;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component("NORMAL_FACTORY")
public class ReclamoNormalFactory implements ReclamoFactory {
    @Override
    public Reclamo crearReclamo(String dni, String direccion, String descripcion, String categoria) {
        String reclamoId = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Reclamo reclamo = new Reclamo(reclamoId, dni, direccion, descripcion, categoria);
        reclamo.setPrioridad("MEDIA");
        return reclamo;
    }
}