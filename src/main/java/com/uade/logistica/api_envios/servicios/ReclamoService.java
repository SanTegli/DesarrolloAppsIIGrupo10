package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dao.ReclamoRepository;
import com.uade.logistica.api_envios.dominio.Reclamo;
import com.uade.logistica.api_envios.dominio.eventos.ReclamoCreadoEvent;
import com.uade.logistica.api_envios.dominio.eventos.ReclamoResueltoEvent;
import com.uade.logistica.api_envios.utilidad.ValidadorReclamo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReclamoService {

    private final ReclamoRepository reclamoRepository;
    private final Map<String, AsignacionAreaStrategy> estrategiasArea;
    private final ReclamoUrgenteFactory urgenteFactory;
    private final ReclamoNormalFactory normalFactory;
    private final ApplicationEventPublisher eventPublisher;

    public ReclamoService(ReclamoRepository reclamoRepository,
                          Map<String, AsignacionAreaStrategy> estrategiasArea,
                          ReclamoUrgenteFactory urgenteFactory,
                          ReclamoNormalFactory normalFactory,
                          ApplicationEventPublisher eventPublisher) {
        this.reclamoRepository = reclamoRepository;
        this.estrategiasArea = estrategiasArea;
        this.urgenteFactory = urgenteFactory;
        this.normalFactory = normalFactory;
        this.eventPublisher = eventPublisher;
    }

    public Reclamo crearReclamo(String dni, String direccion, String descripcion, String categoria) {
        ValidadorReclamo.requerirDniValido(dni);
        ValidadorReclamo.requerirTextoNoVacio(direccion, "dirección");
        ValidadorReclamo.requerirTextoNoVacio(descripcion, "descripción");

        boolean esUrgente = descripcion.toLowerCase().contains("peligro")
                || descripcion.toLowerCase().contains("escuela")
                || descripcion.toLowerCase().contains("urgente")
                || descripcion.toLowerCase().contains("derrumbe");

        // Aplicación del patrón Factory
        ReclamoFactory factory = esUrgente ? urgenteFactory : normalFactory;
        Reclamo reclamo = factory.crearReclamo(dni, direccion, descripcion, categoria);

        // Aplicación del patrón Strategy
        String claveEstrategia = (categoria != null) ? categoria.toUpperCase() : "GENERAL";
        AsignacionAreaStrategy estrategia = estrategiasArea.getOrDefault(
                claveEstrategia,
                (desc) -> "Mesa General de Asuntos Urbanos"
        );
        reclamo.setAreaMunicipal(estrategia.determinarArea(descripcion));

        // Persistencia mediante patrón Repository
        reclamoRepository.guardar(reclamo);

        // Publicación de Evento de Dominio
        eventPublisher.publishEvent(new ReclamoCreadoEvent(reclamo));

        return reclamo;
    }

    public Reclamo resolverReclamo(String reclamoId) {
        ValidadorReclamo.requerirTextoNoVacio(reclamoId, "ID de reclamo");

        Reclamo reclamo = reclamoRepository.buscarPorId(reclamoId)
                .orElseThrow(() -> new NoSuchElementException("Reclamo no encontrado con ID: " + reclamoId));

        reclamo.actualizarEstado("RESUELTO");
        reclamoRepository.guardar(reclamo);

        // Publicación de Evento de Dominio
        eventPublisher.publishEvent(new ReclamoResueltoEvent(reclamo));

        return reclamo;
    }

    public Optional<Reclamo> buscarPorId(String id) {
        return reclamoRepository.buscarPorId(id);
    }

    public List<Reclamo> listarTodos() {
        return reclamoRepository.obtenerTodos();
    }

    public List<Reclamo> listarPorDni(String dni) {
        ValidadorReclamo.requerirDniValido(dni);
        return reclamoRepository.obtenerTodos().stream()
                .filter(r -> r.getDniCiudadano().equals(dni))
                .collect(Collectors.toList());
    }
}
