package ar.edu.uade.reclamos.dominio.modelo;

import static ar.edu.uade.reclamos.dominio.DatosDePrueba.AHORA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 26: un reclamo de "Otro problema" recibe una sugerencia de la IA y un administrador lo recategoriza. */
class RecategorizacionTest {

    private DatosDePrueba datos;
    private Reclamo reclamo;

    @BeforeEach
    void preparar() {
        datos = new DatosDePrueba();
        reclamo = datos.reclamoDeOtroProblema();
    }

    @Test
    @DisplayName("Guarda lo que escribió el vecino")
    void guardaLaPropuesta() {
        assertEquals("Baldosas levantadas", reclamo.getCategoriaPropuesta());
        assertFalse(reclamo.tieneSugerenciaDeCategoria());
    }

    @Test
    @DisplayName("Solo un reclamo de \"otra\" lleva propuesta, y una sola vez")
    void propuestaSoloEnOtra() {
        Reclamo deLuminaria = datos.reclamoIngresado();

        assertThrows(DatosInvalidosException.class, () -> deLuminaria.proponerCategoria("Farol"));
        assertThrows(ReglaNegocioException.class, () -> reclamo.proponerCategoria("Otra cosa"));
    }

    @Test
    @DisplayName("La sugerencia de la IA se guarda una vez y no cambia la categoría")
    void guardaLaSugerencia() {
        assertTrue(reclamo.registrarSugerenciaCategoria(datos.bache, "Parece un pozo."));

        assertSame(datos.bache, reclamo.getCategoriaSugerida());
        assertEquals("Parece un pozo.", reclamo.getMotivoSugerencia());
        assertSame(datos.otro, reclamo.getCategoria());
        assertFalse(reclamo.registrarSugerenciaCategoria(datos.luminaria, "Otra respuesta."));
        assertSame(datos.bache, reclamo.getCategoriaSugerida());
    }

    @Test
    @DisplayName("Si la IA no sugiere ninguna, queda el motivo sin categoría")
    void sinSugerencia() {
        assertTrue(reclamo.registrarSugerenciaCategoria(null, "Ninguna categoría corresponde."));

        assertNull(reclamo.getCategoriaSugerida());
        assertTrue(reclamo.tieneSugerenciaDeCategoria());
    }

    @Test
    @DisplayName("La IA no puede sugerir \"otra\", y un reclamo común no recibe sugerencias")
    void sugerenciasInvalidas() {
        assertThrows(DatosInvalidosException.class,
                () -> reclamo.registrarSugerenciaCategoria(datos.otro, "Sigue siendo otro."));
        assertFalse(datos.reclamoIngresado().registrarSugerenciaCategoria(datos.bache, "Pozo."));
    }

    @Test
    @DisplayName("El administrador recategoriza: cambia el plazo desde la creación y queda en el historial")
    void recategoriza() {
        reclamo.recategorizar(datos.bache, datos.admin, "Es un pozo en la vereda.", AHORA.plusHours(2));

        assertSame(datos.bache, reclamo.getCategoria());
        assertEquals(AHORA.plusHours(120), reclamo.getFechaLimite());
        assertEquals(EstadoReclamo.INGRESADO, reclamo.getEstado());
        HistorialEstado ultimo = reclamo.getHistorial().get(reclamo.getHistorial().size() - 1);
        assertEquals(EstadoReclamo.INGRESADO, ultimo.getEstadoAnterior());
        assertEquals(EstadoReclamo.INGRESADO, ultimo.getEstadoNuevo());
        assertEquals("Categoría: Otro problema → Bache. Es un pozo en la vereda.", ultimo.getObservacion());
        assertSame(datos.admin, ultimo.getUsuario());
        assertEquals("Baldosas levantadas", reclamo.getCategoriaPropuesta());
    }

    @Test
    @DisplayName("La prioridad no baja: queda la mayor entre la actual y la base de la nueva")
    void prioridadNoBaja() {
        reclamo.recategorizar(datos.bache, datos.admin, null, AHORA);
        assertEquals(Prioridad.MEDIA, reclamo.getPrioridad());

        Reclamo otro = datos.reclamoDeOtroProblema();
        otro.definirPrioridad(Prioridad.MEDIA, OrigenPrioridad.IA, "Riesgo moderado.");
        otro.recategorizar(datos.semaforo, datos.admin, null, AHORA);
        assertEquals(Prioridad.ALTA, otro.getPrioridad());
        assertEquals(OrigenPrioridad.CATEGORIA, otro.getOrigenPrioridad());
        assertNull(otro.getMotivoPrioridad());
    }

    @Test
    @DisplayName("Si la prioridad espera a la IA, sube pero sigue esperándola")
    void siguePendienteDeIA() {
        reclamo.definirPrioridad(Prioridad.MEDIA, OrigenPrioridad.PENDIENTE_IA, "La IA no respondió a tiempo.");

        reclamo.recategorizar(datos.semaforo, datos.admin, null, AHORA);

        assertEquals(Prioridad.ALTA, reclamo.getPrioridad());
        assertTrue(reclamo.estaPendienteDeIA());
    }

    @Test
    @DisplayName("Si con el plazo nuevo todavía no venció, deja de estar vencido")
    void recalculaElVencimiento() {
        assertTrue(reclamo.marcarVencido(AHORA.plusHours(73)));

        reclamo.recategorizar(datos.bache, datos.admin, null, AHORA.plusHours(74));

        assertFalse(reclamo.isVencido());
    }

    @Test
    @DisplayName("Solo un administrador, solo sin área, y a una categoría concreta y distinta")
    void reglas() {
        assertThrows(AccesoDenegadoException.class,
                () -> reclamo.recategorizar(datos.bache, datos.ana, null, AHORA));
        assertThrows(AccesoDenegadoException.class,
                () -> reclamo.recategorizar(datos.bache, datos.agenteObras, null, AHORA));
        assertThrows(ReglaNegocioException.class,
                () -> reclamo.recategorizar(datos.otro, datos.admin, null, AHORA));
        Reclamo deLuminaria = datos.reclamoIngresado();
        assertThrows(ReglaNegocioException.class,
                () -> deLuminaria.recategorizar(datos.luminaria, datos.admin, null, AHORA));
        Reclamo asignado = datos.reclamoAsignado();
        assertThrows(ReglaNegocioException.class,
                () -> asignado.recategorizar(datos.bache, datos.admin, null, AHORA));
    }

    @Test
    @DisplayName("Se puede recategorizar un reclamo común que entró con la categoría equivocada")
    void tambienUnoComun() {
        Reclamo deLuminaria = datos.reclamoIngresado();

        deLuminaria.recategorizar(datos.semaforo, datos.admin, "Es el semáforo, no el farol.", AHORA);

        assertSame(datos.semaforo, deLuminaria.getCategoria());
        assertEquals(AHORA.plusHours(24), deLuminaria.getFechaLimite());
    }
}
