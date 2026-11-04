package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.CodigoVerificacionResponse;
import ar.edu.uade.reclamos.aplicacion.rest.dto.UsuarioResponse;
import ar.edu.uade.reclamos.aplicacion.rest.dto.VerificacionSimuladaRequest;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import ar.edu.uade.reclamos.aplicacion.seguridad.UsuarioAutenticado;
import ar.edu.uade.reclamos.aplicacion.verificacion.CodigoEmitido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Verificación del teléfono del ciudadano: un número, una cuenta. */
@RestController
@Tag(name = "Verificación del teléfono", description = "Código de un solo uso confirmado por Telegram")
@RequestMapping("/api/verificacion-telefono")
public class VerificacionTelefonoController {

    private final GestionReclamosFacade facade;
    private final RestMapper mapper;

    public VerificacionTelefonoController(GestionReclamosFacade facade, RestMapper mapper) {
        this.facade = facade;
        this.mapper = mapper;
    }

    @Operation(summary = "Pedir un código para verificar el teléfono",
            description = "Solo ciudadanos. El código vence a los 10 minutos y se piden hasta 3 por hora (429 "
                    + "LIMITE_EXCEDIDO). Con Telegram, el enlace abre el bot con el código cargado.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CodigoVerificacionResponse iniciar(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        CodigoEmitido codigo = facade.iniciarVerificacionTelefono(usuario.id());
        return new CodigoVerificacionResponse(codigo.codigo(), codigo.enlace(), codigo.vence(), codigo.modo());
    }

    @Operation(summary = "Confirmar el código sin Telegram (solo en modo simulado)",
            description = "Para desarrollar y hacer la demo sin bot. Con reclamos.telegram.modo=telegram responde "
                    + "404: el número lo manda Telegram y no se puede escribir a mano.")
    @PostMapping("/simulada")
    public UsuarioResponse confirmarSimulada(
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody VerificacionSimuladaRequest pedido) {
        return mapper.usuario(facade.confirmarVerificacionSimulada(usuario.id(), pedido.codigo(), pedido.telefono()));
    }
}
