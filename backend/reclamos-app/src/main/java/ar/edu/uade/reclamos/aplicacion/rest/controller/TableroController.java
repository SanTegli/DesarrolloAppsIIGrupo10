package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.TableroGestionResponse;
import ar.edu.uade.reclamos.aplicacion.rest.dto.TableroPublicoResponse;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import ar.edu.uade.reclamos.aplicacion.seguridad.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tableros (PR 18): el público no pide sesión; el de gestión, sí. */
@RestController
@Tag(name = "Tableros", description = "Cifras de reclamos: público y de gestión")
@RequestMapping("/api/dashboard")
public class TableroController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public TableroController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @Operation(summary = "Tablero público: creados en el período y cuántos hay hoy en cada situación",
            description = "No necesita sesión. Sin fechas, los últimos 30 días. Hasta 366 días.")
    @GetMapping("/publico")
    public TableroPublicoResponse publico(
            @Parameter(description = "Primer día, incluido (AAAA-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día, incluido (AAAA-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @Positive Long barrioId,
            @RequestParam(required = false) @Positive Long categoriaId) {
        return mapper.tableroPublico(facade.tableroPublico(desde, hasta, barrioId, categoriaId));
    }

    @Operation(summary = "Tablero de gestión: pendientes, vencidos y sin asignar por área y barrio",
            description = "Para agentes (su área) y administradores (todas). Un ciudadano recibe 403.")
    @GetMapping("/gestion")
    public TableroGestionResponse gestion(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return mapper.tableroGestion(facade.tableroGestion(usuario.id()));
    }
}
