package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.AvisoResponse;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bandeja de avisos del usuario identificado por X-Usuario-Id. */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public NotificacionController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @GetMapping
    public List<AvisoResponse> delUsuario(@RequestHeader("X-Usuario-Id") @Positive Long usuarioId) {
        return facade.consultarAvisosDelUsuario(usuarioId).stream().map(mapper::aviso).toList();
    }
}
