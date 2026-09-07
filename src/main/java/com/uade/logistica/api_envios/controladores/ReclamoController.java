package com.uade.logistica.api_envios.controladores;

import com.uade.logistica.api_envios.dominio.Reclamo;
import com.uade.logistica.api_envios.servicios.ReclamoFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reclamos")
public class ReclamoController {

    private final ReclamoFacade reclamoFacade;

    public ReclamoController(ReclamoFacade reclamoFacade) {
        this.reclamoFacade = reclamoFacade;
    }

    public record SolicitudReclamoDto(String dniCiudadano, String direccion, String descripcion, String categoria) {}

    @PostMapping
    public ResponseEntity<Reclamo> crearReclamo(@RequestBody SolicitudReclamoDto dto) {
        Reclamo creado = reclamoFacade.registrarReclamo(dto.dniCiudadano(), dto.direccion(), dto.descripcion(), dto.categoria());
        return ResponseEntity.ok(creado);
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
    public ResponseEntity<List<Reclamo>> listarTodos() {
        return ResponseEntity.ok(reclamoFacade.listarTodos());
    }
}