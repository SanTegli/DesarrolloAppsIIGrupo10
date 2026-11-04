package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.modelo.Administrador;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de cuentas. Solo se registran ciudadanos: los agentes y los administradores los da de alta el municipio.
 * El email se guarda en minúsculas y no se puede repetir, porque es el nombre de usuario para entrar.
 */
@Service
public class ServicioCuentas {

    public static final int MINIMO_CONTRASENA = 8;
    /** BCrypt solo usa los primeros 72 bytes: una contraseña más larga se rechaza en lugar de recortarse. */
    public static final int MAXIMO_BYTES_CONTRASENA = 72;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder contrasenas;

    public ServicioCuentas(UsuarioRepository usuarios, PasswordEncoder contrasenas) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
    }

    @Transactional
    public Ciudadano registrarCiudadano(String dni, String nombre, String apellido, String email, String contrasena) {
        String emailNormalizado = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        String dniNormalizado = dni == null ? null : dni.strip();
        validarContrasena(contrasena);
        Ciudadano ciudadano = new Ciudadano(dniNormalizado, limpiar(nombre), limpiar(apellido), emailNormalizado,
                null);
        if (usuarios.buscarPorEmail(emailNormalizado).isPresent()) {
            throw new ReglaNegocioException("Ya hay una cuenta con ese correo electrónico.");
        }
        if (usuarios.buscarPorDni(dniNormalizado).isPresent()) {
            throw new ReglaNegocioException("Ya hay una cuenta con ese DNI.");
        }
        ciudadano.definirContrasena(contrasenas.encode(contrasena));
        return (Ciudadano) usuarios.guardar(ciudadano);
    }

    /**
     * Bloquea o desbloquea la cuenta de un ciudadano. Solo un administrador. Bloquear no borra nada: los
     * reclamos y el historial quedan; la cuenta puede entrar y consultar, pero no crear ni reabrir.
     */
    @Transactional
    public Usuario cambiarBloqueo(Long actorId, Long usuarioId, boolean bloqueado) {
        if (!(buscar(actorId) instanceof Administrador)) {
            throw new AccesoDenegadoException("Solo un administrador puede bloquear cuentas.");
        }
        if (!(buscar(usuarioId) instanceof Ciudadano ciudadano)) {
            throw new ReglaNegocioException("Solo se bloquean cuentas de ciudadanos.");
        }
        if (bloqueado) {
            ciudadano.bloquear();
        } else {
            ciudadano.desbloquear();
        }
        return usuarios.guardar(ciudadano);
    }

    private Usuario buscar(Long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException("usuario", id));
    }

    private static void validarContrasena(String contrasena) {
        if (contrasena == null || contrasena.length() < MINIMO_CONTRASENA) {
            throw new DatosInvalidosException("La contraseña tiene que tener al menos " + MINIMO_CONTRASENA
                    + " caracteres.");
        }
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_CONTRASENA) {
            throw new DatosInvalidosException("La contraseña es demasiado larga.");
        }
    }

    private static String limpiar(String texto) {
        return texto == null ? null : texto.strip();
    }
}
