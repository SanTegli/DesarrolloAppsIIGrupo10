package ar.edu.uade.reclamos.dominio.modelo;

import static ar.edu.uade.reclamos.dominio.DatosDePrueba.AHORA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La IA no respondió en el alta y revisa la prioridad después, por la cola: sube, nunca baja. */
class RevisionPrioridadPorIATest {

    private DatosDePrueba datos;
    private Reclamo reclamo;

    @BeforeEach
    void preparar() {
        datos = new DatosDePrueba();
        reclamo = datos.reclamoIngresado();
        reclamo.definirPrioridad(Prioridad.MEDIA, OrigenPrioridad.PENDIENTE_IA, "La IA no respondió a tiempo.");
    }

    @Test
    @DisplayName("Si la IA sugiere más, la prioridad sube con origen IA y el motivo de la IA")
    void subeConLaSugerencia() {
        assertTrue(reclamo.estaPendienteDeIA());

        boolean cambio = reclamo.completarPrioridadPorIA(Prioridad.CRITICA, "Cables expuestos.");

        assertTrue(cambio);
        assertEquals(Prioridad.CRITICA, reclamo.getPrioridad());
        assertEquals(OrigenPrioridad.IA, reclamo.getOrigenPrioridad());
        assertEquals("Cables expuestos.", reclamo.getMotivoPrioridad());
        assertFalse(reclamo.estaPendienteDeIA());
    }

    @Test
    @DisplayName("Si la IA sugiere lo mismo, queda con origen IA pero sin cambio")
    void igualSinCambio() {
        boolean cambio = reclamo.completarPrioridadPorIA(Prioridad.MEDIA, "Farol apagado sin peligro.");

        assertFalse(cambio);
        assertEquals(Prioridad.MEDIA, reclamo.getPrioridad());
        assertEquals(OrigenPrioridad.IA, reclamo.getOrigenPrioridad());
    }

    @Test
    @DisplayName("Si la IA sugiere menos, se mantiene la actual con origen categoría")
    void noBaja() {
        boolean cambio = reclamo.completarPrioridadPorIA(Prioridad.BAJA, "Poco urgente.");

        assertFalse(cambio);
        assertEquals(Prioridad.MEDIA, reclamo.getPrioridad());
        assertEquals(OrigenPrioridad.CATEGORIA, reclamo.getOrigenPrioridad());
        assertEquals("La IA sugirió BAJA; se mantiene MEDIA, que es mayor.", reclamo.getMotivoPrioridad());
    }

    @Test
    @DisplayName("Si venció mientras esperaba a la IA, no baja la prioridad que subió el vencimiento")
    void respetaElVencimiento() {
        assertTrue(reclamo.marcarVencido(AHORA.plusHours(49)));
        assertEquals(Prioridad.ALTA, reclamo.getPrioridad());

        boolean cambio = reclamo.completarPrioridadPorIA(Prioridad.MEDIA, "Farol apagado.");

        assertFalse(cambio);
        assertEquals(Prioridad.ALTA, reclamo.getPrioridad());
    }

    @Test
    @DisplayName("Solo se completa una vez: la segunda respuesta de la IA es un error de regla")
    void soloUnaVez() {
        reclamo.completarPrioridadPorIA(Prioridad.ALTA, "Riesgo.");

        assertThrows(ReglaNegocioException.class, () -> reclamo.completarPrioridadPorIA(Prioridad.CRITICA, "Más."));
        assertEquals(Prioridad.ALTA, reclamo.getPrioridad());
    }

    @Test
    @DisplayName("Si la IA nunca responde, queda la prioridad de la categoría con origen respaldo")
    void abandonar() {
        reclamo.abandonarRevisionPorIA("La IA no respondió en 4 intentos.");

        assertEquals(Prioridad.MEDIA, reclamo.getPrioridad());
        assertEquals(OrigenPrioridad.RESPALDO, reclamo.getOrigenPrioridad());
        assertEquals("La IA no respondió en 4 intentos.", reclamo.getMotivoPrioridad());
        assertFalse(reclamo.estaPendienteDeIA());
    }

    @Test
    @DisplayName("Abandonar un reclamo que ya no espera a la IA no cambia nada")
    void abandonarSinPendiente() {
        reclamo.completarPrioridadPorIA(Prioridad.ALTA, "Riesgo.");

        reclamo.abandonarRevisionPorIA("Tarde.");

        assertEquals(OrigenPrioridad.IA, reclamo.getOrigenPrioridad());
        assertEquals("Riesgo.", reclamo.getMotivoPrioridad());
    }
}
