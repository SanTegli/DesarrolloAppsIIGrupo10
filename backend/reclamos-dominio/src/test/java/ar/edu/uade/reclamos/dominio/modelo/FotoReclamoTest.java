package ar.edu.uade.reclamos.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La foto del reclamo: el dominio guarda solo la referencia, una vez. */
class FotoReclamoTest {

    private final Reclamo reclamo = new DatosDePrueba().reclamoAsignado();

    @Test
    @DisplayName("Un reclamo nuevo no tiene foto hasta que se registra")
    void registraLaReferencia() {
        assertEquals(Optional.empty(), reclamo.getFoto());

        reclamo.registrarFoto("3f2a9c0d4b7e4e1a9d5c6b8a7f0e1d2c.jpg");

        assertEquals(Optional.of("3f2a9c0d4b7e4e1a9d5c6b8a7f0e1d2c.jpg"), reclamo.getFoto());
    }

    @Test
    @DisplayName("La foto no se reemplaza: es la prueba del alta")
    void noSeReemplaza() {
        reclamo.registrarFoto("primera.jpg");

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> reclamo.registrarFoto("segunda.jpg"));
        assertTrue(error.getMessage().contains("ya tiene una foto"));
        assertEquals(Optional.of("primera.jpg"), reclamo.getFoto());
    }

    @Test
    @DisplayName("Una referencia vacía o demasiado larga no se acepta")
    void referenciaInvalida() {
        assertThrows(DatosInvalidosException.class, () -> reclamo.registrarFoto(" "));
        assertThrows(DatosInvalidosException.class, () -> reclamo.registrarFoto(null));
        assertThrows(DatosInvalidosException.class, () -> reclamo.registrarFoto("x".repeat(65)));
        assertEquals(Optional.empty(), reclamo.getFoto());
    }
}
