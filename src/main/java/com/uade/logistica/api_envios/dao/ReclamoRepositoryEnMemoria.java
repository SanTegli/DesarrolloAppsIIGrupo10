package com.uade.logistica.api_envios.dao;

import com.uade.logistica.api_envios.dominio.Reclamo;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class ReclamoRepositoryEnMemoria implements ReclamoRepository {
    private final Map<String, Reclamo> storage = new HashMap<>();

    @Override
    public Reclamo guardar(Reclamo reclamo) {
        storage.put(reclamo.getId(), reclamo);
        return reclamo;
    }

    @Override
    public Optional<Reclamo> buscarPorId(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Reclamo> obtenerTodos() {
        return new ArrayList<>(storage.values());
    }
}