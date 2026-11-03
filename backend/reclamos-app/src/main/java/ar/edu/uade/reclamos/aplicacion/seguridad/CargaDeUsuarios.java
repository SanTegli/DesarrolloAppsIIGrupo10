package ar.edu.uade.reclamos.aplicacion.seguridad;

import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca al usuario por su email para el login. Un usuario sin contraseña no puede entrar; uno desactivado
 * tampoco. Spring responde igual en los dos casos y cuando el email no existe, para no revelar qué cuentas hay.
 */
@Component
public class CargaDeUsuarios implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public CargaDeUsuarios(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return usuarios.buscarPorEmail(normalizar(email))
                .filter(usuario -> usuario.tieneContrasena())
                .map(usuario -> new UsuarioAutenticado(usuario.getId(), usuario.getEmail(),
                        usuario.getHashContrasena(), usuario.isActivo()))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario inexistente o sin contraseña"));
    }

    static String normalizar(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
