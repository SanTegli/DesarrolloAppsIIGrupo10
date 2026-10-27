package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.AvisoResponse;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bandeja de avisos del usuario identificado por X-Usuario-Id. */
@RestController
@Tag(name = "Avisos", description = "Bandeja de avisos del usuario")
@RequestMapping("/api/notificaciones")
public class NotificacionController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public NotificacionController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @Operation(summary = "Avisos recibidos por el usuario, del más nuevo al más viejo")
    @GetMapping
    public List<AvisoResponse> delUsuario(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId) {
        return facade.consultarAvisosDelUsuario(usuarioId).stream().map(mapper::aviso).toList();
    }
}
