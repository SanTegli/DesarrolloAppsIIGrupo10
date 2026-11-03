package ar.edu.uade.reclamos.aplicacion.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF para una aplicación de una sola página, como lo recomienda la documentación de Spring Security.
 *
 * <p>El token viaja en la cookie {@code XSRF-TOKEN}, que la interfaz lee y devuelve en el encabezado
 * {@code X-XSRF-TOKEN}: ese valor llega tal cual. Si el token llega como parámetro de un formulario, se usa la
 * versión enmascarada (protección BREACH). Cada respuesta deja la cookie actualizada.
 */
final class CsrfParaAplicacionDeUnaPagina implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plano = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler enmascarado = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
        enmascarado.handle(request, response, token);
        // Carga el token ya: así la cookie sale en cada respuesta, también en la primera.
        token.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
        String encabezado = request.getHeader(token.getHeaderName());
        return (StringUtils.hasText(encabezado) ? plano : enmascarado).resolveCsrfTokenValue(request, token);
    }
}
