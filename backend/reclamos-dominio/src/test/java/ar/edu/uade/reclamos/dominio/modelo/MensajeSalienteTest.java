package ar.edu.uade.reclamos.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MensajeSalienteTest {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 2, 18, 0);

    @Test
    void naceComoPendienteSinIntentos() {
        MensajeSaliente mensaje = nuevo();

        assertTrue(mensaje.isPendiente());
        assertEquals(EstadoMensaje.PENDIENTE, mensaje.getEstado());
        assertEquals(0, mensaje.getIntentos());
        assertNull(mensaje.getFechaPublicacion());
    }

    @Test
    void cadaFallaSumaUnIntentoYGuardaElErrorRecortado() {
        MensajeSaliente mensaje = nuevo();
        mensaje.registrarFalla("Connection refused");
        mensaje.registrarFalla("x".repeat(500));

        assertEquals(2, mensaje.getIntentos());
        assertEquals(MensajeSaliente.LONGITUD_MAXIMA_ERROR, mensaje.getUltimoError().length());
        assertTrue(mensaje.isPendiente());
    }

    @Test
    void publicadoLimpiaElError() {
        MensajeSaliente mensaje = nuevo();
        mensaje.registrarFalla(null);
        mensaje.marcarPublicado(AHORA.plusSeconds(2));

        assertFalse(mensaje.isPendiente());
        assertEquals(AHORA.plusSeconds(2), mensaje.getFechaPublicacion());
        assertNull(mensaje.getUltimoError());
        assertEquals(1, mensaje.getIntentos());
    }

    @Test
    void validaLosDatosObligatorios() {
        assertThrows(DatosInvalidosException.class,
                () -> new MensajeSaliente(" ", "aviso.x", "Tipo", "{}", AHORA));
        assertThrows(DatosInvalidosException.class,
                () -> new MensajeSaliente("e", "aviso.x", "Tipo", "x".repeat(4001), AHORA));
    }

    private static MensajeSaliente nuevo() {
        return new MensajeSaliente("6f1c2a9e-4b7d-4e1f-9a52-3c8d0e7b1a44", "aviso.reclamo-creado", "ReclamoCreado",
                "{}", AHORA);
    }
}
