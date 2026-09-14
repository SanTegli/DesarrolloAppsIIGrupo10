package com.uade.logistica.api_envios.dao;

import com.uade.logistica.api_envios.dominio.Ciudadano;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CiudadanoRepositoryEnMemoria implements CiudadanoRepository {
    private final Map<String, Ciudadano> storage = new ConcurrentHashMap<>();

    @Override
    public Ciudadano guardar(Ciudadano ciudadano) {
        storage.put(ciudadano.getDni(), ciudadano);
        return ciudadano;
    }

    @Override
    public Optional<Ciudadano> buscarPorDni(String dni) {
        return Optional.ofNullable(storage.get(dni));
    }

    @Override
    public List<Ciudadano> obtenerTodos() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public boolean existePorDni(String dni) {
        return storage.containsKey(dni);
    }
}
