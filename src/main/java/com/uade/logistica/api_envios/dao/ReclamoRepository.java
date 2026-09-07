package com.uade.logistica.api_envios.dao;

import com.uade.logistica.api_envios.dominio.Reclamo;
import java.util.List;
import java.util.Optional;

public interface ReclamoRepository {
    Reclamo guardar(Reclamo reclamo);
    Optional<Reclamo> buscarPorId(String id);
    List<Reclamo> obtenerTodos();
}