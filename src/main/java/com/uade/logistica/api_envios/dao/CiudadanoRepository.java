package com.uade.logistica.api_envios.dao;

import com.uade.logistica.api_envios.dominio.Ciudadano;
import java.util.List;
import java.util.Optional;

public interface CiudadanoRepository {
    Ciudadano guardar(Ciudadano ciudadano);
    Optional<Ciudadano> buscarPorDni(String dni);
    List<Ciudadano> obtenerTodos();
    boolean existePorDni(String dni);
}
