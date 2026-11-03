package ar.edu.uade.reclamos.aplicacion.seguridad;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * El usuario que guarda la sesión: solo su id y su email. El rol no se guarda acá: los permisos se deciden
 * con el usuario de la base en cada operación, así un cambio de rol o una baja valen enseguida.
 *
 * <p>Después del login Spring borra el hash ({@link #eraseCredentials()}): la sesión nunca lo conserva.
 */
public final class UsuarioAutenticado implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final boolean activo;
    private String hashContrasena;

    public UsuarioAutenticado(Long id, String email, String hashContrasena, boolean activo) {
        this.id = id;
        this.email = email;
        this.hashContrasena = hashContrasena;
        this.activo = activo;
    }

    public Long id() {
        return id;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return hashContrasena;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** Sin autoridades: las reglas por rol viven en el dominio y en {@link PermisosReclamo}. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public void eraseCredentials() {
        this.hashContrasena = null;
    }

    @Override
    public String toString() {
        return "UsuarioAutenticado[id=" + id + "]";
    }
}
