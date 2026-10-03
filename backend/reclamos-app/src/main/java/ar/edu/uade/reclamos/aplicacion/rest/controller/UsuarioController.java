package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.UsuarioResponse;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public UsuarioController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return facade.usuarios().stream().map(mapper::usuario).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable @Positive Long id) {
        return mapper.usuario(facade.usuario(id));
    }
}
