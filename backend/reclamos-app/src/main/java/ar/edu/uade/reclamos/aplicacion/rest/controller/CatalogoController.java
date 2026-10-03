package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.*;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogoController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public CatalogoController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return facade.categorias().stream().map(mapper::categoria).toList();
    }

    @GetMapping("/barrios")
    public List<BarrioResponse> barrios() {
        return facade.barrios().stream().map(mapper::barrio).toList();
    }

    @GetMapping("/areas")
    public List<AreaResponse> areas() {
        return facade.areas().stream().map(mapper::area).toList();
    }
}
