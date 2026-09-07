package com.uade.logistica.api_envios.servicios;

import com.uade.logistica.api_envios.dao.ReclamoRepository;
import com.uade.logistica.api_envios.dominio.Reclamo;
import com.uade.logistica.api_envios.dominio.ReclamoEvento;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReclamoFacade {
    private final ReclamoRepository repository;
    private final Map<String, AsignacionAreaStrategy> estrategiasArea;
    private final ReclamoFactory urgenteFactory;
    private final ReclamoFactory normalFactory;
    private final List<ReclamoObserver> observadores;

    public ReclamoFacade(ReclamoRepository repository, 
                         Map<String, AsignacionAreaStrategy> estrategiasArea,
                         ReclamoUrgenteFactory urgenteFactory,
                         ReclamoNormalFactory normalFactory,
                         List<ReclamoObserver> observadores) {
        this.repository = repository;
        this.estrategiasArea = estrategiasArea;
        this.urgenteFactory = urgenteFactory;
        this.normalFactory = normalFactory;
        this.observadores = observadores;
    }

    public Reclamo registrarReclamo(String dni, String direccion, String descripcion, String categoria) {
        boolean esUrgente = descripcion.toLowerCase().contains("peligro") 
                         || descripcion.toLowerCase().contains("escuela") 
                         || descripcion.toLowerCase().contains("urgente");
        
        ReclamoFactory factory = esUrgente ? urgenteFactory : normalFactory;
        Reclamo reclamo = factory.crearReclamo(dni, direccion, descripcion, categoria);

        // Aplicación del patrón Strategy
        AsignacionAreaStrategy estrategia = estrategiasArea.getOrDefault(
                categoria.toUpperCase(), (desc) -> "Mesa General de Asuntos Urbanos"
        );
        reclamo.setAreaMunicipal(estrategia.determinarArea(descripcion));

        // Persistencia
        repository.guardar(reclamo);

        // Notificación mediante Observer
        dispararEvento(new ReclamoEvento(reclamo.getId(), "ReclamoCreado", "Asignado a: " + reclamo.getAreaMunicipal()));

        return reclamo;
    }

    public Reclamo resolverReclamo(String reclamoId) {
        Reclamo reclamo = repository.buscarPorId(reclamoId)
                .orElseThrow(() -> new NoSuchElementException("Reclamo no encontrado"));

        reclamo.actualizarEstado("RESUELTO");
        repository.guardar(reclamo);

        dispararEvento(new ReclamoEvento(reclamoId, "ReclamoResuelto", "Reclamo solucionado con éxito"));
        return reclamo;
    }

    public Optional<Reclamo> consultarReclamo(String reclamoId) {
        return repository.buscarPorId(reclamoId);
    }

    public List<Reclamo> listarTodos() {
        return repository.obtenerTodos();
    }

    private void dispararEvento(ReclamoEvento evento) {
        for (ReclamoObserver obs : observadores) {
            obs.notificar(evento);
        }
    }
}