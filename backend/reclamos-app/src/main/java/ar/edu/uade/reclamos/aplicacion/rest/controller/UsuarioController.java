package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.BloqueoRequest;
import ar.edu.uade.reclamos.aplicacion.rest.dto.UsuarioResponse;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import ar.edu.uade.reclamos.aplicacion.seguridad.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Usuarios", description = "Usuarios del sistema: la lista completa, solo para administradores")
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public UsuarioController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @Operation(summary = "Listar usuarios (solo administradores)")
    @GetMapping
    public List<UsuarioResponse> listar(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return facade.usuarios(usuario.id()).stream().map(mapper::usuario).toList();
    }

    @Operation(summary = "Consultar un usuario por id (el propio, o cualquiera si es administrador)")
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
                                  @PathVariable @Positive Long id) {
        return mapper.usuario(facade.usuario(usuario.id(), id));
    }

    @Operation(summary = "Bloquear o desbloquear la cuenta de un ciudadano (solo administradores)",
            description = "Bloquear no borra los reclamos ni el historial: la cuenta puede entrar y consultar, pero "
                    + "no crear ni reabrir reclamos (403 CUENTA_BLOQUEADA).")
    @PatchMapping("/{id}/bloqueo")
    public UsuarioResponse cambiarBloqueo(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable @Positive Long id, @Valid @RequestBody BloqueoRequest pedido) {
        return mapper.usuario(facade.cambiarBloqueo(usuario.id(), id, pedido.bloqueado()));
    }
}
