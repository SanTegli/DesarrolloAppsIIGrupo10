package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.*;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Catálogos", description = "Categorías, barrios y áreas")
@RequestMapping("/api")
public class CatalogoController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public CatalogoController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @Operation(summary = "Listar categorías con SLA y prioridad base")
    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return facade.categorias().stream().map(mapper::categoria).toList();
    }

    @Operation(summary = "Listar barrios")
    @GetMapping("/barrios")
    public List<BarrioResponse> barrios() {
        return facade.barrios().stream().map(mapper::barrio).toList();
    }

    @Operation(summary = "Listar áreas con su cobertura")
    @GetMapping("/areas")
    public List<AreaResponse> areas() {
        return facade.areas().stream().map(mapper::area).toList();
    }
}
