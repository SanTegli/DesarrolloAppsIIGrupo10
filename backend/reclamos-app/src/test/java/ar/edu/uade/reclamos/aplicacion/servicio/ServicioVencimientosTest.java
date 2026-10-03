package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.dominio.evento.ReclamoVencido;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServicioVencimientosTest {
    /** Los reclamos del escenario se crean a las 10:00 con 48 horas de plazo. */
    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final DoblesEnMemoria.Reclamos reclamos = new DoblesEnMemoria.Reclamos();
    private final DoblesEnMemoria.Eventos eventos = new DoblesEnMemoria.Eventos();

    private ServicioVencimientos servicioCuandoPasaron(int horas) {
        return new ServicioVencimientos(reclamos, eventos, Clock.offset(e.reloj, Duration.ofHours(horas)));
    }

    @Test
    void marcaElReclamoVencidoSubeLaPrioridadYPublicaElEvento() {
        Reclamo reclamo = reclamos.guardar(e.asignado());
        LocalDateTime ahora = LocalDateTime.now(e.reloj).plusHours(49);

        int marcados = servicioCuandoPasaron(49).marcarVencidos();

        assertEquals(1, marcados);
        assertTrue(reclamo.isVencido());
        assertEquals(Prioridad.MEDIA, reclamo.getPrioridad());
        assertEquals(List.of(new ReclamoVencido(reclamo.getNumero(), e.area.getId(), Prioridad.MEDIA,
                reclamo.getFechaLimite(), ahora)), eventos.publicados);
    }

    @Test
    void unReclamoSinAreaVenceConAreaNulaEnElEvento() {
        Reclamo reclamo = reclamos.guardar(e.ingresado());

        servicioCuandoPasaron(49).marcarVencidos();

        ReclamoVencido evento = (ReclamoVencido) eventos.publicados.get(0);
        assertEquals(reclamo.getNumero(), evento.numeroReclamo());
        assertNull(evento.areaId());
    }

    @Test
    void unaSegundaEjecucionNoVuelveAMarcarNiAPublicar() {
        Reclamo reclamo = reclamos.guardar(e.asignado());
        ServicioVencimientos servicio = servicioCuandoPasaron(49);
        servicio.marcarVencidos();

        int marcados = servicio.marcarVencidos();

        assertEquals(0, marcados);
        assertEquals(1, eventos.publicados.size());
        assertEquals(Prioridad.MEDIA, reclamo.getPrioridad());
    }

    @Test
    void antesDeLaFechaLimiteNoHayVencidos() {
        Reclamo reclamo = reclamos.guardar(e.asignado());

        assertEquals(0, servicioCuandoPasaron(47).marcarVencidos());
        assertEquals(0, servicioCuandoPasaron(48).marcarVencidos());

        assertFalse(reclamo.isVencido());
        assertTrue(eventos.publicados.isEmpty());
    }

    @Test
    void losReclamosResueltosOCanceladosNoVencen() {
        Reclamo resuelto = reclamos.guardar(e.resuelto());
        Reclamo cancelado = e.ingresado();
        cancelado.cambiarEstado(EstadoReclamo.CANCELADO, e.ciudadano, null, LocalDateTime.now(e.reloj));
        reclamos.guardar(cancelado);

        int marcados = servicioCuandoPasaron(500).marcarVencidos();

        assertEquals(0, marcados);
        assertFalse(resuelto.isVencido());
        assertFalse(cancelado.isVencido());
    }

    @Test
    void marcaTodosLosVencidosEnUnaMismaEjecucion() {
        reclamos.guardar(e.ingresado());
        reclamos.guardar(e.asignado());
        reclamos.guardar(e.resuelto());

        assertEquals(2, servicioCuandoPasaron(49).marcarVencidos());
        assertEquals(List.of("ReclamoVencido", "ReclamoVencido"), eventos.nombres());
    }
}
