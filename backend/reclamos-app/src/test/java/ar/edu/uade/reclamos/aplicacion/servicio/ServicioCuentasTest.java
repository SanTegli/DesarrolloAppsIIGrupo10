package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Registro de ciudadanos: email en minúsculas y único, DNI único y contraseña guardada como hash. */
class ServicioCuentasTest {

    private final DoblesEnMemoria.Usuarios usuarios = new DoblesEnMemoria.Usuarios();
    // Costo mínimo de BCrypt: el test no necesita que sea lento.
    private final PasswordEncoder contrasenas = new BCryptPasswordEncoder(4);
    private final ServicioCuentas cuentas = new ServicioCuentas(usuarios, contrasenas);

    @Test
    void registraUnCiudadanoConElEmailEnMinusculasYLaContrasenaComoHash() {
        Ciudadano laura = cuentas.registrarCiudadano(" 33444555 ", " Laura ", "Martínez", " Laura@Example.ORG ",
                "una-clave-larga");

        assertThat(laura.getRol()).isEqualTo(Rol.CIUDADANO);
        assertThat(laura.getEmail()).isEqualTo("laura@example.org");
        assertThat(laura.getDni()).isEqualTo("33444555");
        assertThat(laura.getNombreCompleto()).isEqualTo("Laura Martínez");
        assertThat(laura.getHashContrasena()).isNotEqualTo("una-clave-larga");
        assertThat(contrasenas.matches("una-clave-larga", laura.getHashContrasena())).isTrue();
        assertThat(usuarios.guardados).containsExactly(laura);
    }

    @Test
    void noRepiteEmailSinImportarMayusculasNiDni() {
        cuentas.registrarCiudadano("33444555", "Laura", "Martínez", "laura@example.org", "una-clave-larga");

        assertThatThrownBy(() -> cuentas.registrarCiudadano("34555666", "Otra", "Persona", "LAURA@example.org",
                "una-clave-larga")).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("correo");
        assertThatThrownBy(() -> cuentas.registrarCiudadano("33444555", "Otra", "Persona", "otra@example.org",
                "una-clave-larga")).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("DNI");
        assertThat(usuarios.guardados).hasSize(1);
    }

    @Test
    void rechazaContrasenasCortasOMasLargasQueLoQueUsaBcrypt() {
        assertThatThrownBy(() -> cuentas.registrarCiudadano("33444555", "Laura", "Martínez", "laura@example.org",
                "corta")).isInstanceOf(DatosInvalidosException.class);
        // 40 letras ñ son 80 bytes en UTF-8: BCrypt ignoraría lo que pasa de 72.
        assertThatThrownBy(() -> cuentas.registrarCiudadano("33444555", "Laura", "Martínez", "laura@example.org",
                "ñ".repeat(40))).isInstanceOf(DatosInvalidosException.class);
        assertThat(usuarios.guardados).isEmpty();
    }

    @Test
    void soloElAdministradorBloqueaYSoloCuentasDeCiudadanos() {
        EscenarioAplicacion e = new EscenarioAplicacion();
        DoblesEnMemoria.Usuarios todos = new DoblesEnMemoria.Usuarios(e.ciudadano, e.agente, e.administrador);
        ServicioCuentas servicio = new ServicioCuentas(todos, contrasenas);

        assertThat(servicio.cambiarBloqueo(e.administrador.getId(), e.ciudadano.getId(), true).isBloqueado()).isTrue();
        assertThat(servicio.cambiarBloqueo(e.administrador.getId(), e.ciudadano.getId(), false).isBloqueado()).isFalse();
        assertThatThrownBy(() -> servicio.cambiarBloqueo(e.agente.getId(), e.ciudadano.getId(), true))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.cambiarBloqueo(e.administrador.getId(), e.agente.getId(), true))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.cambiarBloqueo(e.administrador.getId(), 99L, true))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(e.ciudadano.isBloqueado()).isFalse();
    }
}
