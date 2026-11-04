package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.*;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import ar.edu.uade.reclamos.aplicacion.seguridad.UsuarioAutenticado;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.aplicacion.foto.FotoSubida;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Operaciones sobre reclamos. Quién opera sale de la sesión ({@link UsuarioAutenticado}); qué puede hacer lo
 * decide el dominio con el usuario guardado en la base.
 */
@RestController
@Tag(name = "Reclamos", description = "Alta, consulta, cambios de estado y asignación")
@RequestMapping("/api/reclamos")
public class ReclamoController {
    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public ReclamoController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    /**
     * Alta con foto: {@code multipart/form-data} con la parte {@code reclamo} (el JSON de siempre, con tipo
     * {@code application/json}) y la parte {@code foto} (JPEG o PNG, hasta 5 MB y 4000 × 4000 píxeles).
     */
    @Operation(summary = "Crear un reclamo con su foto (solo ciudadanos)",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(name = "reclamo", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @ApiResponse(responseCode = "413", description = "La foto supera el tamaño o las dimensiones (ARCHIVO_DEMASIADO_GRANDE)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReclamoResponse> crear(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestPart("reclamo") CrearReclamoRequest request,
            @RequestPart("foto") MultipartFile foto) throws IOException {
        Reclamo reclamo = facade.crear(usuario.id(), request.categoriaId(), request.barrioId(),
                request.descripcion(), request.direccion(), mapper.ubicacionElegida(request.ubicacion()),
                new FotoSubida(foto.getBytes()));
        return ResponseEntity.created(URI.create("/api/reclamos/" + reclamo.getNumero()))
                .body(respuesta(usuario.id(), reclamo));
    }

    /** La foto re-codificada a JPEG, sin metadatos. La ve quien puede ver el detalle del reclamo. */
    @Operation(summary = "Foto del reclamo (mismos permisos que el detalle)")
    @ApiResponse(responseCode = "200", description = "JPEG sin metadatos",
            content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{numero}/foto")
    public ResponseEntity<byte[]> foto(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
                                       @PathVariable String numero) {
        // Sin "produces": así un error (403, 404) sale en JSON como el resto de la API.
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(facade.foto(usuario.id(), numero));
    }

    @Operation(summary = "Listar los reclamos que ve el usuario")
    @GetMapping
    public List<ResumenReclamoResponse> listar(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) EstadoReclamo estado,
            @RequestParam(required = false) @Positive Long areaId,
            @RequestParam(required = false) @Positive Long ciudadanoId) {
        return facade.listar(usuario.id(), estado, areaId, ciudadanoId).stream().map(mapper::resumen).toList();
    }

    @Operation(summary = "Consultar un reclamo con su historial y acciones disponibles")
    @GetMapping("/{numero}")
    public ReclamoResponse buscar(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
                                  @PathVariable String numero) {
        return respuesta(usuario.id(), facade.buscarPorNumero(usuario.id(), numero));
    }

    @Operation(summary = "Cambiar el estado de un reclamo")
    @PatchMapping("/{numero}/estado")
    public ReclamoResponse cambiarEstado(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable String numero,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return respuesta(usuario.id(),
                facade.cambiarEstado(usuario.id(), numero, request.estado(), request.observacion()));
    }

    @Operation(summary = "Asignar o reasignar el área (solo administradores)")
    @PatchMapping("/{numero}/asignacion")
    public ReclamoResponse asignar(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
                                   @PathVariable String numero,
                                   @Valid @RequestBody AsignarReclamoRequest request) {
        return respuesta(usuario.id(),
                facade.asignar(usuario.id(), numero, request.areaId(), request.observacion()));
    }

    @Operation(summary = "Avisos generados por los eventos de un reclamo")
    @GetMapping("/{numero}/notificaciones")
    public List<NotificacionResponse> notificaciones(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable String numero) {
        return facade.consultarNotificaciones(usuario.id(), numero).stream().map(mapper::notificacion).toList();
    }

    private ReclamoResponse respuesta(Long usuarioId, Reclamo reclamo) {
        return mapper.reclamo(reclamo, facade.accionesDisponibles(usuarioId, reclamo.getNumero()));
    }
}
