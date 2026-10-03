package ar.edu.uade.reclamos.dominio.fabrica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReclamoFactoryTest {

    private static final Clock RELOJ_FIJO = Clock.fixed(Instant.parse("2026-10-05T13:30:45.123Z"), ZoneOffset.UTC);
    private static final LocalDateTime CREACION = LocalDateTime.of(2026, 10, 5, 13, 30, 45);

    private DatosDePrueba datos;
    private ReclamoFactory factory;

    @BeforeEach
    void preparar() {
        datos = new DatosDePrueba();
        factory = new ReclamoFactory(RELOJ_FIJO);
    }

    @Test
    @DisplayName("Crea un reclamo consistente: número, estado, fechas, prioridad e historial")
    void creaUnReclamoConsistente() {
        Reclamo reclamo = factory.crear(datos.ana, datos.luminaria, datos.bernal,
                "Farol apagado frente a la plaza", "Belgrano 450");

        assertTrue(reclamo.getNumero().matches("REC-[0-9A-F]{8}"), "número: " + reclamo.getNumero());
        assertEquals(EstadoReclamo.INGRESADO, reclamo.getEstado());
        assertEquals(CREACION, reclamo.getFechaCreacion());
        assertEquals(Prioridad.MEDIA, reclamo.getPrioridad());
        assertSame(datos.ana, reclamo.getCiudadano());
        assertSame(datos.luminaria, reclamo.getCategoria());
        assertSame(datos.bernal, reclamo.getBarrio());
        assertNull(reclamo.getArea());
        assertNull(reclamo.getId(), "el id lo asigna la persistencia");
        assertEquals(1, reclamo.getHistorial().size());
        assertEquals(CREACION, reclamo.getHistorial().get(0).getFecha());
    }

    @Test
    @DisplayName("La fecha límite es la de creación más el SLA de la categoría")
    void fechaLimiteSegunSla() {
        Reclamo luminaria = factory.crear(datos.ana, datos.luminaria, datos.bernal, "Farol apagado", "Belgrano 450");
        Reclamo bache = factory.crear(datos.ana, datos.bache, datos.bernal, "Pozo profundo", "Zapiola 120");

        assertEquals(CREACION.plusHours(48), luminaria.getFechaLimite());
        assertEquals(CREACION.plusHours(120), bache.getFechaLimite());
    }

    @Test
    @DisplayName("La prioridad inicial es la base de la categoría")
    void prioridadBaseDeLaCategoria() {
        Reclamo bache = factory.crear(datos.ana, datos.bache, datos.bernal, "Pozo profundo", "Zapiola 120");

        assertEquals(Prioridad.BAJA, bache.getPrioridad());
    }

    @Test
    @DisplayName("Cada reclamo recibe un número distinto")
    void numerosDistintos() {
        Reclamo uno = factory.crear(datos.ana, datos.bache, datos.bernal, "Pozo profundo", "Zapiola 120");
        Reclamo otro = factory.crear(datos.ana, datos.bache, datos.bernal, "Pozo profundo", "Zapiola 120");

        assertNotEquals(uno.getNumero(), otro.getNumero());
    }

    @Test
    @DisplayName("Descripción y dirección se guardan sin espacios sobrantes")
    void limpiaLosTextos() {
        Reclamo reclamo = factory.crear(datos.ana, datos.bache, datos.bernal, "  Pozo profundo ", " Zapiola 120  ");

        assertEquals("Pozo profundo", reclamo.getDescripcion());
        assertEquals("Zapiola 120", reclamo.getDireccion());
    }

    @Test
    @DisplayName("Sin ciudadano, categoría, barrio, descripción o dirección no hay reclamo")
    void datosObligatorios() {
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(null, datos.bache, datos.bernal, "Pozo profundo", "Zapiola 120"));
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(datos.ana, null, datos.bernal, "Pozo profundo", "Zapiola 120"));
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(datos.ana, datos.bache, null, "Pozo profundo", "Zapiola 120"));
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(datos.ana, datos.bache, datos.bernal, "", "Zapiola 120"));
        assertThrows(DatosInvalidosException.class,
                () -> factory.crear(datos.ana, datos.bache, datos.bernal, "Pozo profundo", "  "));
    }

    @Test
    @DisplayName("Un ciudadano inactivo no puede crear reclamos")
    void ciudadanoInactivo() {
        datos.ana.desactivar();

        assertThrows(ReglaNegocioException.class,
                () -> factory.crear(datos.ana, datos.bache, datos.bernal, "Pozo profundo", "Zapiola 120"));
    }
}
