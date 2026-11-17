package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.SugerenciasUbicacionResponse;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ubicaciones", description = "Búsqueda de la dirección del reclamo en el mapa (Georef)")
@RequestMapping("/api/ubicaciones")
public class UbicacionController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public UbicacionController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @Operation(summary = "Buscar la dirección de un reclamo",
            description = "Consulta la dirección en el geocodificador, una vez por municipio, y devuelve las opciones "
                    + "con coordenadas y su municipio: elegir una define el municipio del reclamo. Si el "
                    + "geocodificador no responde para ningún municipio, devuelve disponible: false.")
    @GetMapping("/sugerencias")
    public SugerenciasUbicacionResponse sugerencias(
            @RequestParam @NotBlank @Size(min = 3, max = 200) String direccion) {
        return mapper.sugerencias(facade.sugerirUbicaciones(direccion));
    }
}
