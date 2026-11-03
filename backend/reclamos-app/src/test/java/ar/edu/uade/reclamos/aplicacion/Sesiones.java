package ar.edu.uade.reclamos.aplicacion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import ar.edu.uade.reclamos.aplicacion.seguridad.UsuarioAutenticado;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Pedidos de MockMvc con sesión iniciada, en lugar del viejo encabezado X-Usuario-Id. */
public final class Sesiones {

    private Sesiones() {
    }

    /** Con la sesión del usuario y el token CSRF, como los manda la interfaz después del login. */
    public static RequestPostProcessor como(long usuarioId) {
        RequestPostProcessor sesion = sinCsrf(usuarioId);
        RequestPostProcessor token = csrf();
        return pedido -> token.postProcessRequest(sesion.postProcessRequest(pedido));
    }

    /** Con sesión pero sin token CSRF: un POST o PATCH así tiene que ser rechazado. */
    public static RequestPostProcessor sinCsrf(long usuarioId) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(usuarioId, "usuario" + usuarioId + "@test.org", null, true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                usuario.getAuthorities()));
    }
}
