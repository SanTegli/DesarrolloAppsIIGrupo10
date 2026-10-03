package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaAsignacion;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioAsignacionTest {
    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final LocalDateTime ahora = LocalDateTime.now(e.reloj);
    private final List<List<AreaMunicipal>> candidatasRecibidas = new ArrayList<>();
    private final EstrategiaAsignacion ultimaCandidata = (reclamo, candidatas) -> {
        candidatasRecibidas.add(candidatas);
        return candidatas.isEmpty() ? Optional.empty() : Optional.of(candidatas.getLast());
    };
    private ServicioAsignacion servicio;

    @BeforeEach
    void areasQueCubrenElBarrio() {
        for (AreaMunicipal area : List.of(e.area, e.otraArea)) {
            area.agregarCategoria(e.categoria);
            area.agregarBarrio(e.barrio);
        }
        servicio = new ServicioAsignacion(new DoblesEnMemoria.Areas(e.area, e.otraArea), ultimaCandidata);
    }

    @Test
    void laAsignacionAutomaticaUsaElAreaQueEligeLaEstrategia() {
        Reclamo reclamo = e.ingresado();

        boolean asignado = servicio.asignarAutomaticamente(reclamo, 1L, 1L, ahora);

        assertTrue(asignado);
        assertEquals(EstadoReclamo.ASIGNADO, reclamo.getEstado());
        assertSame(e.otraArea, reclamo.getArea());
        assertEquals(List.of(List.of(e.area, e.otraArea)), candidatasRecibidas);
        assertTrue(reclamo.getHistorial().get(1).fueAutomatico());
        assertEquals("Asignación automática", reclamo.getHistorial().get(1).getObservacion());
    }

    @Test
    void sinAreasCandidatasElReclamoQuedaIngresado() {
        Reclamo reclamo = e.ingresado();

        boolean asignado = servicio.asignarAutomaticamente(reclamo, 99L, 1L, ahora);

        assertFalse(asignado);
        assertEquals(EstadoReclamo.INGRESADO, reclamo.getEstado());
        assertNull(reclamo.getArea());
        assertEquals(1, reclamo.getHistorial().size());
        assertEquals(List.of(List.of()), candidatasRecibidas);
    }

    @Test
    void unAreaInactivaNoEsCandidata() {
        e.otraArea.desactivar();
        Reclamo reclamo = e.ingresado();

        servicio.asignarAutomaticamente(reclamo, 1L, 1L, ahora);

        assertSame(e.area, reclamo.getArea());
    }

    @Test
    void laAsignacionManualAplicaElAreaElegidaYRegistraQuienAsigno() {
        Reclamo reclamo = e.ingresado();

        servicio.asignarManualmente(reclamo, 2L, e.administrador, "Manual", ahora);

        assertEquals(EstadoReclamo.ASIGNADO, reclamo.getEstado());
        assertSame(e.otraArea, reclamo.getArea());
        assertSame(e.administrador, reclamo.getHistorial().get(1).getUsuario());
        assertTrue(candidatasRecibidas.isEmpty(), "la asignación manual no consulta la estrategia");
    }

    @Test
    void laAsignacionManualAUnAreaInexistenteNoModificaElReclamo() {
        Reclamo reclamo = e.ingresado();

        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.asignarManualmente(reclamo, 99L, e.administrador, null, ahora));

        assertEquals(EstadoReclamo.INGRESADO, reclamo.getEstado());
    }

    @Test
    void soloElAdministradorAsignaAMano() {
        Reclamo reclamo = e.ingresado();

        assertThrows(AccesoDenegadoException.class,
                () -> servicio.asignarManualmente(reclamo, 1L, e.agente, null, ahora));
        assertThrows(AccesoDenegadoException.class,
                () -> servicio.asignarManualmente(reclamo, 1L, e.ciudadano, null, ahora));

        assertNull(reclamo.getArea());
    }
}
