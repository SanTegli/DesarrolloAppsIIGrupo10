package ar.edu.uade.reclamos.aplicacion.seguridad;

import ar.edu.uade.reclamos.aplicacion.rest.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Las respuestas de Spring Security con el mismo cuerpo de error que el resto de la API: 401 sin sesión y 403
 * si falta o no coincide el token CSRF. Así la interfaz trata todos los errores igual.
 */
@Component
public class RespuestasDeSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;
    private final Clock reloj;

    public RespuestasDeSeguridad(ObjectMapper json, Clock reloj) {
        this.json = json;
        this.reloj = reloj;
    }

    /** Pedido a una ruta protegida sin sesión. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
            throws IOException {
        escribir(response, request, 401, "NO_AUTENTICADO", "Hay que iniciar sesión para usar esta función.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException error)
            throws IOException {
        if (error instanceof CsrfException) {
            escribir(response, request, 403, "CSRF_INVALIDO",
                    "Falta el token de seguridad o venció. Recargá la página e intentá de nuevo.");
        } else {
            escribir(response, request, 403, "ACCESO_DENEGADO", "No tenés permiso para esta operación.");
        }
    }

    void escribir(HttpServletResponse response, HttpServletRequest request, int status, String codigo,
                  String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), new ErrorResponse(status, codigo, mensaje, List.of(),
                request.getRequestURI(), LocalDateTime.now(reloj)));
    }
}
