package ar.edu.uade.reclamos.dominio.estrategia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.dominio.modelo.OrigenPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import org.junit.jupiter.api.Test;

class PrioridadCalculadaTest {

    @Test
    void porCategoriaNoTieneMotivo() {
        PrioridadCalculada calculada = PrioridadCalculada.porCategoria(Prioridad.MEDIA);

        assertEquals(Prioridad.MEDIA, calculada.prioridad());
        assertEquals(OrigenPrioridad.CATEGORIA, calculada.origen());
        assertNull(calculada.motivo());
    }

    @Test
    void elMotivoSeRecortaYUnoVacioQuedaNulo() {
        assertEquals("Riesgo eléctrico", new PrioridadCalculada(Prioridad.CRITICA, OrigenPrioridad.IA,
                "  Riesgo eléctrico ").motivo());
        assertNull(new PrioridadCalculada(Prioridad.ALTA, OrigenPrioridad.IA, "   ").motivo());
    }

    @Test
    void rechazaDatosFaltantesOMotivoDemasiadoLargo() {
        assertThrows(DatosInvalidosException.class, () -> new PrioridadCalculada(null, OrigenPrioridad.IA, null));
        assertThrows(DatosInvalidosException.class, () -> new PrioridadCalculada(Prioridad.ALTA, null, null));
        assertThrows(DatosInvalidosException.class, () -> new PrioridadCalculada(Prioridad.ALTA, OrigenPrioridad.IA,
                "x".repeat(PrioridadCalculada.LONGITUD_MAXIMA_MOTIVO + 1)));
    }
}
