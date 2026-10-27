package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.*;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Reclamos", description = "Alta, consulta, cambios de estado y asignación")
@RequestMapping("/api/reclamos")
public class ReclamoController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public ReclamoController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @Operation(summary = "Crear un reclamo (solo ciudadanos)")
    @PostMapping
    public ResponseEntity<ReclamoResponse> crear(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                                @Valid @RequestBody CrearReclamoRequest request) {
        Reclamo reclamo = facade.crear(usuarioId, request.categoriaId(), request.barrioId(),
                request.descripcion(), request.direccion());
        return ResponseEntity.created(URI.create("/api/reclamos/" + reclamo.getNumero()))
                .body(respuesta(usuarioId, reclamo));
    }

    @Operation(summary = "Listar los reclamos que ve el usuario")
    @GetMapping
    public List<ResumenReclamoResponse> listar(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                              @RequestParam(required = false) EstadoReclamo estado,
                                              @RequestParam(required = false) @Positive Long areaId,
                                              @RequestParam(required = false) @Positive Long ciudadanoId) {
        return facade.listar(usuarioId, estado, areaId, ciudadanoId).stream().map(mapper::resumen).toList();
    }

    @Operation(summary = "Consultar un reclamo con su historial y acciones disponibles")
    @GetMapping("/{numero}")
    public ReclamoResponse buscar(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                  @PathVariable String numero) {
        return respuesta(usuarioId, facade.buscarPorNumero(usuarioId, numero));
    }

    @Operation(summary = "Cambiar el estado de un reclamo")
    @PatchMapping("/{numero}/estado")
    public ReclamoResponse cambiarEstado(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                         @PathVariable String numero,
                                         @Valid @RequestBody CambiarEstadoRequest request) {
        return respuesta(usuarioId, facade.cambiarEstado(usuarioId, numero, request.estado(), request.observacion()));
    }

    @Operation(summary = "Asignar o reasignar el área (solo administradores)")
    @PatchMapping("/{numero}/asignacion")
    public ReclamoResponse asignar(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                   @PathVariable String numero,
                                   @Valid @RequestBody AsignarReclamoRequest request) {
        return respuesta(usuarioId, facade.asignar(usuarioId, numero, request.areaId(), request.observacion()));
    }

    @Operation(summary = "Avisos generados por los eventos de un reclamo")
    @GetMapping("/{numero}/notificaciones")
    public List<NotificacionResponse> notificaciones(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                                    @PathVariable String numero) {
        return facade.consultarNotificaciones(usuarioId, numero).stream().map(mapper::notificacion).toList();
    }

    private ReclamoResponse respuesta(Long usuarioId, Reclamo reclamo) {
        return mapper.reclamo(reclamo, facade.accionesDisponibles(usuarioId, reclamo.getNumero()));
    }
}
