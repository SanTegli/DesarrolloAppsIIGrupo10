package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dao.CiudadanoRepository;
import com.uade.logistica.api_envios.dominio.Ciudadano;
import com.uade.logistica.api_envios.utilidad.ValidadorReclamo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CiudadanoService {

    private final CiudadanoRepository ciudadanoRepository;

    public CiudadanoService(CiudadanoRepository ciudadanoRepository) {
        this.ciudadanoRepository = ciudadanoRepository;
    }

    public Ciudadano registrarOActualizar(String dni, String nombre, String email) {
        ValidadorReclamo.requerirDniValido(dni);
        ValidadorReclamo.requerirTextoNoVacio(nombre, "nombre");
        ValidadorReclamo.requerirEmailValido(email);

        Ciudadano ciudadano = new Ciudadano(dni, nombre, email);
        return ciudadanoRepository.guardar(ciudadano);
    }

    public Optional<Ciudadano> buscarPorDni(String dni) {
        ValidadorReclamo.requerirDniValido(dni);
        return ciudadanoRepository.buscarPorDni(dni);
    }

    public Ciudadano asegurarExistencia(String dni, String nombrePorDefecto, String emailPorDefecto) {
        return buscarPorDni(dni).orElseGet(() -> {
            String nombre = (nombrePorDefecto != null && !nombrePorDefecto.isBlank()) ? nombrePorDefecto : "Ciudadano Anónimo";
            String email = (emailPorDefecto != null && !emailPorDefecto.isBlank()) ? emailPorDefecto : "contacto." + dni + "@ciudad.gob.ar";
            return registrarOActualizar(dni, nombre, email);
        });
    }

    public List<Ciudadano> listarTodos() {
        return ciudadanoRepository.obtenerTodos();
    }
}
