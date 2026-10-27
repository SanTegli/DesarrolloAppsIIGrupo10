package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.UsuarioResponse;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Usuarios", description = "Usuarios para el selector de identidad")
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public UsuarioController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @Operation(summary = "Listar usuarios")
    @GetMapping
    public List<UsuarioResponse> listar() {
        return facade.usuarios().stream().map(mapper::usuario).toList();
    }

    @Operation(summary = "Consultar un usuario por id")
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable @Positive Long id) {
        return mapper.usuario(facade.usuario(id));
    }
}
