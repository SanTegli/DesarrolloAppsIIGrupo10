package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.*;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reclamos")
public class ReclamoController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public ReclamoController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ReclamoResponse> crear(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                                @Valid @RequestBody CrearReclamoRequest request) {
        Reclamo reclamo = facade.crear(usuarioId, request.categoriaId(), request.barrioId(),
                request.descripcion(), request.direccion());
        return ResponseEntity.created(URI.create("/api/reclamos/" + reclamo.getNumero()))
                .body(respuesta(usuarioId, reclamo));
    }

    @GetMapping
    public List<ResumenReclamoResponse> listar(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                              @RequestParam(required = false) EstadoReclamo estado,
                                              @RequestParam(required = false) @Positive Long areaId,
                                              @RequestParam(required = false) @Positive Long ciudadanoId) {
        return facade.listar(usuarioId, estado, areaId, ciudadanoId).stream().map(mapper::resumen).toList();
    }

    @GetMapping("/{numero}")
    public ReclamoResponse buscar(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                  @PathVariable String numero) {
        return respuesta(usuarioId, facade.buscarPorNumero(usuarioId, numero));
    }

    @PatchMapping("/{numero}/estado")
    public ReclamoResponse cambiarEstado(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                         @PathVariable String numero,
                                         @Valid @RequestBody CambiarEstadoRequest request) {
        return respuesta(usuarioId, facade.cambiarEstado(usuarioId, numero, request.estado(), request.observacion()));
    }

    @PatchMapping("/{numero}/asignacion")
    public ReclamoResponse asignar(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                   @PathVariable String numero,
                                   @Valid @RequestBody AsignarReclamoRequest request) {
        return respuesta(usuarioId, facade.asignar(usuarioId, numero, request.areaId(), request.observacion()));
    }

    @GetMapping("/{numero}/notificaciones")
    public List<NotificacionResponse> notificaciones(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId,
                                                    @PathVariable String numero) {
        return facade.consultarNotificaciones(usuarioId, numero).stream().map(mapper::notificacion).toList();
    }

    private ReclamoResponse respuesta(Long usuarioId, Reclamo reclamo) {
        return mapper.reclamo(reclamo, facade.accionesDisponibles(usuarioId, reclamo.getNumero()));
    }
}
