package ar.edu.uade.reclamos.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import org.junit.jupiter.api.Test;

/** La contraseña del usuario: el dominio guarda el hash que recibe y nunca la deja vacía. */
class UsuarioTest {

    private static final String HASH = "$2a$10$Fj7CGx6hDi2DyQSJv4Bat.Crnxp12nWpSIXthWYde6v/q.bYhjFjW";

    @Test
    void unUsuarioNuevoNoTieneContrasena() {
        Ciudadano ana = new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null);

        assertFalse(ana.tieneContrasena());
        assertNull(ana.getHashContrasena());
    }

    @Test
    void guardaElHashRecibido() {
        Ciudadano ana = new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null);

        ana.definirContrasena(HASH);

        assertTrue(ana.tieneContrasena());
        assertEquals(HASH, ana.getHashContrasena());
    }

    @Test
    void noAceptaUnHashVacio() {
        Ciudadano ana = new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null);

        assertThrows(DatosInvalidosException.class, () -> ana.definirContrasena(" "));
        assertThrows(DatosInvalidosException.class, () -> ana.definirContrasena(null));
        assertFalse(ana.tieneContrasena());
    }
}
