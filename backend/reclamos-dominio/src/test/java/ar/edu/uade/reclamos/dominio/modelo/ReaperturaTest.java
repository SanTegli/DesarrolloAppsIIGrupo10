package ar.edu.uade.reclamos.dominio.modelo;

import static ar.edu.uade.reclamos.dominio.DatosDePrueba.AHORA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reaperturas con límite: una por reclamo, dentro de los 7 días de la resolución y con motivo. */
class ReaperturaTest {

    private static final PoliticaReapertura UNA_EN_7_DIAS = PoliticaReapertura.PREDETERMINADA;
    private DatosDePrueba datos;
    private Reclamo reclamo;

    @BeforeEach
    void resuelto() {
        datos = new DatosDePrueba();
        reclamo = datos.reclamoAsignado();
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteAlumbrado, null, AHORA);
        reclamo.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, null, AHORA.plusHours(1));
    }

    @Test
    @DisplayName("Se puede reabrir una vez dentro del plazo, con motivo, y queda contada")
    void reabreUnaVezDentroDelPlazo() {
        assertTrue(reclamo.puedeReabrir(AHORA.plusDays(7), UNA_EN_7_DIAS));

        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, "Sigue apagada", AHORA.plusDays(7), UNA_EN_7_DIAS);

        assertEquals(EstadoReclamo.EN_PROCESO, reclamo.getEstado());
        assertEquals(1, reclamo.getCantidadReaperturas());
        assertEquals(AHORA.plusHours(1), reclamo.getFechaUltimaResolucion().orElseThrow());
    }

    @Test
    @DisplayName("Sin motivo no se reabre")
    void sinMotivoNoSeReabre() {
        assertThrows(DatosInvalidosException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, "  ", AHORA.plusHours(2), UNA_EN_7_DIAS));
        assertThrows(DatosInvalidosException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, null, AHORA.plusHours(2), UNA_EN_7_DIAS));
        assertEquals(EstadoReclamo.RESUELTO, reclamo.getEstado());
    }

    @Test
    @DisplayName("Pasado el plazo desde la última resolución, no se reabre")
    void fueraDePlazoNoSeReabre() {
        LocalDateTime tarde = AHORA.plusHours(1).plusDays(7).plusMinutes(1);

        assertFalse(reclamo.puedeReabrir(tarde, UNA_EN_7_DIAS));
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, "Sigue", tarde, UNA_EN_7_DIAS));
        assertTrue(error.getMessage().contains("7 días"));
        assertEquals(EstadoReclamo.RESUELTO, reclamo.getEstado());
    }

    @Test
    @DisplayName("La segunda reapertura se rechaza aunque el agente haya vuelto a resolver")
    void elContadorNoSeReiniciaAlVolverAResolver() {
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, "Sigue apagada", AHORA.plusHours(2), UNA_EN_7_DIAS);
        reclamo.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, "Cambiada", AHORA.plusHours(3));

        assertFalse(reclamo.puedeReabrir(AHORA.plusHours(4), UNA_EN_7_DIAS));
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, "Otra vez", AHORA.plusHours(4), UNA_EN_7_DIAS));
        assertTrue(error.getMessage().contains("ya se reabrió una vez"));
        assertEquals(1, reclamo.getCantidadReaperturas());
    }

    @Test
    @DisplayName("Los dos números se configuran: dos reaperturas en 1 día")
    void politicaConfigurable() {
        PoliticaReapertura dosEnUnDia = new PoliticaReapertura(2, Duration.ofDays(1));
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, "Sigue", AHORA.plusHours(2), dosEnUnDia);
        reclamo.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, null, AHORA.plusHours(3));
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.ana, "Sigue", AHORA.plusHours(4), dosEnUnDia);

        assertEquals(2, reclamo.getCantidadReaperturas());
        assertThrows(DatosInvalidosException.class, () -> new PoliticaReapertura(-1, Duration.ofDays(1)));
        assertThrows(DatosInvalidosException.class, () -> new PoliticaReapertura(1, Duration.ZERO));
    }

    @Test
    @DisplayName("Un reclamo que no está resuelto no se ofrece para reabrir")
    void soloSeReabreUnoResuelto() {
        Reclamo asignado = datos.reclamoAsignado();
        assertFalse(asignado.puedeReabrir(AHORA, UNA_EN_7_DIAS));
        assertTrue(asignado.getFechaUltimaResolucion().isEmpty());
    }
}
