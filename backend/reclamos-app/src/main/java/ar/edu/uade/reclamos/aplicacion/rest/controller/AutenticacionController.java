package ar.edu.uade.reclamos.aplicacion.rest.controller;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.aplicacion.rest.dto.LoginRequest;
import ar.edu.uade.reclamos.aplicacion.rest.dto.RegistroRequest;
import ar.edu.uade.reclamos.aplicacion.rest.dto.UsuarioResponse;
import ar.edu.uade.reclamos.aplicacion.rest.mapper.RestMapper;
import ar.edu.uade.reclamos.aplicacion.seguridad.CredencialesInvalidasException;
import ar.edu.uade.reclamos.aplicacion.seguridad.UsuarioAutenticado;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * Login, registro y datos de la sesión. El login guarda el usuario en la sesión del servidor; el navegador
 * solo recibe la cookie {@code JSESSIONID}, que es {@code HttpOnly}.
 */
@RestController
@Tag(name = "Sesión", description = "Login, registro, usuario actual y cierre de sesión")
@RequestMapping("/api/auth")
public class AutenticacionController {

    private final GestionReclamosFacade facade;
    private final RestMapper mapper;
    private final AuthenticationManager autenticador;
    private final SecurityContextRepository contextos;
    private final SecurityContextHolderStrategy contextoActual = SecurityContextHolder.getContextHolderStrategy();

    public AutenticacionController(GestionReclamosFacade facade, RestMapper mapper,
                                   AuthenticationManager autenticador, SecurityContextRepository contextos) {
        this.facade = facade;
        this.mapper = mapper;
        this.autenticador = autenticador;
        this.contextos = contextos;
    }

    @Operation(summary = "Iniciar sesión con email y contraseña",
            description = "Responde el usuario y deja la sesión en la cookie JSESSIONID. Como todo POST, necesita "
                    + "el encabezado X-XSRF-TOKEN con el valor de la cookie XSRF-TOKEN.")
    @PostMapping("/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest pedido, HttpServletRequest request,
                                 HttpServletResponse response) {
        Authentication autenticacion;
        try {
            autenticacion = autenticador.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    pedido.email().strip().toLowerCase(Locale.ROOT), pedido.contrasena()));
        } catch (AuthenticationException error) {
            throw new CredencialesInvalidasException();
        }
        // Sesión con otro id: alguien que conocía el id anterior no hereda el login (fijación de sesión).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext contexto = contextoActual.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        contextoActual.setContext(contexto);
        contextos.saveContext(contexto, request, response);
        UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacion.getPrincipal();
        return mapper.usuario(facade.usuario(usuario.id()));
    }

    @Operation(summary = "Registrarse como ciudadano",
            description = "Crea la cuenta; después hay que iniciar sesión. Los agentes y administradores no se "
                    + "registran: los da de alta el municipio.")
    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponse> registro(@Valid @RequestBody RegistroRequest pedido) {
        Ciudadano ciudadano = facade.registrarCiudadano(pedido.dni(), pedido.nombre(), pedido.apellido(),
                pedido.email(), pedido.contrasena());
        return ResponseEntity.created(URI.create("/api/usuarios/" + ciudadano.getId()))
                .body(mapper.usuario(ciudadano));
    }

    @Operation(summary = "Usuario de la sesión", description = "401 NO_AUTENTICADO si no hay sesión.")
    @GetMapping("/yo")
    public UsuarioResponse yo(@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return mapper.usuario(facade.usuario(usuario.id()));
    }

    /**
     * Solo para que la operación figure en el contrato: el pedido lo atiende antes el filtro de cierre de sesión
     * de Spring Security ({@code ConfiguracionSeguridad}), que invalida la sesión y responde 204.
     */
    @Operation(summary = "Cerrar sesión", description = "Invalida la sesión y responde 204. Necesita el token CSRF.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Lo resuelve el filtro de logout; este método no se ejecuta.
    }
}
