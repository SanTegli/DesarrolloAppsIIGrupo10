package com.uade.logistica.api_envios.controladores;

import com.uade.logistica.api_envios.dominio.Ciudadano;
import com.uade.logistica.api_envios.dominio.Reclamo;
import com.uade.logistica.api_envios.dominio.ReclamoEvento;
import com.uade.logistica.api_envios.servicios.ReclamoFacade;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/reclamos")
public class ReclamoController {

    private final ReclamoFacade reclamoFacade;

    public ReclamoController(ReclamoFacade reclamoFacade) {
        this.reclamoFacade = reclamoFacade;
    }

    public record SolicitudReclamoDto(
            String dniCiudadano,
            String direccion,
            String descripcion,
            String categoria,
            String nombreCiudadano,
            String emailCiudadano
    ) {}

    @PostMapping
    public ResponseEntity<Reclamo> crearReclamo(@RequestBody SolicitudReclamoDto dto) {
        Reclamo creado = reclamoFacade.registrarReclamo(
                dto.dniCiudadano(),
                dto.direccion(),
                dto.descripcion(),
                dto.categoria(),
                dto.nombreCiudadano(),
                dto.emailCiudadano()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reclamo> obtenerPorId(@PathVariable String id) {
        return reclamoFacade.consultarReclamo(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/resolver")
    public ResponseEntity<Reclamo> resolver(@PathVariable String id) {
        Reclamo actualizado = reclamoFacade.resolverReclamo(id);
        return ResponseEntity.ok(actualizado);
    }

    @GetMapping
    public ResponseEntity<List<Reclamo>> listar(@RequestParam(required = false) String dni) {
        if (dni != null && !dni.isBlank()) {
            return ResponseEntity.ok(reclamoFacade.listarReclamosPorCiudadano(dni));
        }
        return ResponseEntity.ok(reclamoFacade.listarTodos());
    }

    @GetMapping("/ciudadanos")
    public ResponseEntity<List<Ciudadano>> listarCiudadanos() {
        return ResponseEntity.ok(reclamoFacade.listarCiudadanos());
    }

    @GetMapping("/ciudadanos/{dni}")
    public ResponseEntity<Ciudadano> obtenerCiudadanoPorDni(@PathVariable String dni) {
        return reclamoFacade.consultarCiudadano(dni)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/notificaciones")
    public ResponseEntity<List<ReclamoEvento>> listarNotificaciones() {
        return ResponseEntity.ok(reclamoFacade.obtenerHistorialNotificaciones());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarValidacion(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> manejarNoEncontrado(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}