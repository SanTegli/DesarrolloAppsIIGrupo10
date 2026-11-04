package ar.edu.uade.reclamos.dominio.modelo;

import static ar.edu.uade.reclamos.dominio.DatosDePrueba.AHORA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Apoyos y la regla de qué reclamo puede aparecer como posible duplicado. */
class ApoyoTest {

    private final DatosDePrueba datos = new DatosDePrueba();

    @Test
    @DisplayName("Otro ciudadano apoya un reclamo abierto, sin cambiarle la prioridad ni el estado")
    void apoyaUnReclamoAbierto() {
        Reclamo reclamo = datos.reclamoAsignado();
        Prioridad antes = reclamo.getPrioridad();

        Apoyo apoyo = new Apoyo(datos.bruno, reclamo, AHORA);

        assertSame(datos.bruno, apoyo.getCiudadano());
        assertSame(reclamo, apoyo.getReclamo());
        assertEquals(AHORA, apoyo.getFecha());
        assertEquals(antes, reclamo.getPrioridad());
        assertEquals(EstadoReclamo.ASIGNADO, reclamo.getEstado());
    }

    @Test
    @DisplayName("Nadie apoya su propio reclamo")
    void noSeApoyaElPropio() {
        Reclamo reclamo = datos.reclamoAsignado();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> new Apoyo(datos.ana, reclamo, AHORA));
        assertEquals("No podés apoyar tu propio reclamo.", error.getMessage());
    }

    @Test
    @DisplayName("Un reclamo resuelto ya no se apoya")
    void soloAbiertos() {
        Reclamo reclamo = datos.reclamoAsignado();
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteAlumbrado, null, AHORA);
        new Apoyo(datos.bruno, reclamo, AHORA);
        reclamo.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, null, AHORA);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> new Apoyo(datos.bruno, reclamo, AHORA));
        assertTrue(error.getMessage().contains("ya no está abierto"));
    }

    @Test
    @DisplayName("Faltan datos: no se crea")
    void datosObligatorios() {
        Reclamo reclamo = datos.reclamoAsignado();

        assertThrows(DatosInvalidosException.class, () -> new Apoyo(null, reclamo, AHORA));
        assertThrows(DatosInvalidosException.class, () -> new Apoyo(datos.bruno, null, AHORA));
        assertThrows(DatosInvalidosException.class, () -> new Apoyo(datos.bruno, reclamo, null));
    }

    @Test
    @DisplayName("Solo un reclamo abierto y ubicado en el mapa puede aparecer como posible duplicado")
    void puedeSerDuplicado() {
        Reclamo reclamo = datos.reclamoAsignado();
        assertFalse(reclamo.puedeSerDuplicado(), "sin ubicación confirmada no hay cercanía");

        reclamo.registrarUbicacion(Ubicacion.confirmada(-34.736, -58.323, "GRL BELGRANO 450, Bernal", "georef"));
        assertTrue(reclamo.puedeSerDuplicado());

        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, datos.agenteAlumbrado, null, AHORA);
        assertTrue(reclamo.puedeSerDuplicado());
        reclamo.cambiarEstado(EstadoReclamo.RESUELTO, datos.agenteAlumbrado, null, AHORA);
        assertFalse(reclamo.puedeSerDuplicado());
    }
}
