package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dominio.Ciudadano;
import com.uade.logistica.api_envios.dominio.Reclamo;
import com.uade.logistica.api_envios.dominio.ReclamoEvento;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Patrón Facade: Proporciona una interfaz unificada y de alto nivel
 * para interactuar con los subsistemas y servicios del negocio
 * (ReclamoService, CiudadanoService, NotificacionService).
 */
@Service
public class ReclamoFacade {

    private final ReclamoService reclamoService;
    private final CiudadanoService ciudadanoService;
    private final NotificacionService notificacionService;

    public ReclamoFacade(ReclamoService reclamoService,
                         CiudadanoService ciudadanoService,
                         NotificacionService notificacionService) {
        this.reclamoService = reclamoService;
        this.ciudadanoService = ciudadanoService;
        this.notificacionService = notificacionService;
    }

    public Reclamo registrarReclamo(String dni, String direccion, String descripcion, String categoria) {
        return registrarReclamo(dni, direccion, descripcion, categoria, null, null);
    }

    public Reclamo registrarReclamo(String dni, String direccion, String descripcion, String categoria,
                                   String nombreCiudadano, String emailCiudadano) {
        // 1. Coordinación con CiudadanoService para asegurar existencia del ciudadano
        ciudadanoService.asegurarExistencia(dni, nombreCiudadano, emailCiudadano);

        // 2. Coordinación con ReclamoService (aplica Factory, Strategy, Persistencia y emite Domain Event)
        return reclamoService.crearReclamo(dni, direccion, descripcion, categoria);
    }

    public Reclamo resolverReclamo(String reclamoId) {
        return reclamoService.resolverReclamo(reclamoId);
    }

    public Optional<Reclamo> consultarReclamo(String reclamoId) {
        return reclamoService.buscarPorId(reclamoId);
    }

    public List<Reclamo> listarTodos() {
        return reclamoService.listarTodos();
    }

    public List<Reclamo> listarReclamosPorCiudadano(String dni) {
        return reclamoService.listarPorDni(dni);
    }

    public Optional<Ciudadano> consultarCiudadano(String dni) {
        return ciudadanoService.buscarPorDni(dni);
    }

    public List<Ciudadano> listarCiudadanos() {
        return ciudadanoService.listarTodos();
    }

    public List<ReclamoEvento> obtenerHistorialNotificaciones() {
        return notificacionService.obtenerHistorialEventos();
    }
}