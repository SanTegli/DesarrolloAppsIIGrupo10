package ar.edu.uade.reclamos.aplicacion.evento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.aplicacion.estrategia.AsignacionPorJurisdiccion;
import ar.edu.uade.reclamos.aplicacion.estrategia.PrioridadPorCategoria;
import ar.edu.uade.reclamos.aplicacion.seguridad.PermisosReclamo;
import ar.edu.uade.reclamos.aplicacion.servicio.ReclamoService;
import ar.edu.uade.reclamos.aplicacion.servicio.ServicioAsignacion;
import ar.edu.uade.reclamos.aplicacion.servicio.ServicioNotificaciones;
import ar.edu.uade.reclamos.aplicacion.servicio.ServicioVencimientos;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Patrón Observer de punta a punta, sin Spring: los servicios reales publican eventos y el listener
 * real genera los avisos. Los ids del escenario son: ciudadano 1, agente de Obras 3, agente de Vial 4,
 * administrador 5.
 */
class FlujoDeAvisosTest {
    private static final long CIUDADANO = 1L;
    private static final long AGENTE_OBRAS = 3L;
    private static final long AGENTE_VIAL = 4L;
    private static final long ADMINISTRADOR = 5L;
    private static final long CATEGORIA_SIN_AREA = 2L;

    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final DoblesEnMemoria.Reclamos reclamos = new DoblesEnMemoria.Reclamos();
    private final DoblesEnMemoria.Notificaciones avisos = new DoblesEnMemoria.Notificaciones();
    private final DoblesEnMemoria.Eventos eventos = new DoblesEnMemoria.Eventos();
    private final DoblesEnMemoria.Usuarios usuarios = new DoblesEnMemoria.Usuarios(
            e.ciudadano, e.otroCiudadano, e.agente, e.otroAgente, e.administrador);
    private final DoblesEnMemoria.Categorias categorias = new DoblesEnMemoria.Categorias(e.categoria);
    private ReclamoService servicio;

    @BeforeEach
    void conectarServiciosYListener() {
        e.area.agregarCategoria(e.categoria);
        e.area.agregarBarrio(e.barrio);
        var sinArea = new ar.edu.uade.reclamos.dominio.modelo.Categoria("Sin cobertura", "Prueba", 24, Prioridad.BAJA);
        sinArea.setId(CATEGORIA_SIN_AREA);
        categorias.guardar(sinArea);
        var areas = new DoblesEnMemoria.Areas(e.area, e.otraArea);
        servicio = new ReclamoService(usuarios, categorias, new DoblesEnMemoria.Barrios(e.barrio), reclamos,
                e.factory, new PrioridadPorCategoria(), new ServicioAsignacion(areas, new AsignacionPorJurisdiccion()),
                new PermisosReclamo(), e.reloj, eventos);
        eventos.conectar(new NotificacionesReclamoListener(new ServicioNotificaciones(reclamos, avisos, usuarios)));
    }

    private String crear(long categoriaId) {
        return servicio.crear(CIUDADANO, categoriaId, 1L, "Pozo en la calle", "Belgrano 450").getNumero();
    }

    @Test
    void crearConAsignacionAutomaticaAvisaAlCiudadanoYAlAgenteDelArea() {
        String numero = crear(1L);

        assertEquals(List.of("ReclamoCreado", "ReclamoAsignado"), eventos.nombres());
        assertEquals(List.of(CIUDADANO, CIUDADANO, AGENTE_OBRAS), avisos.destinatarios());
        assertEquals(List.of(
                "Tu reclamo " + numero + " fue ingresado.",
                "Tu reclamo " + numero + " fue asignado al area Obras.",
                "El reclamo " + numero + " fue asignado a tu area Obras."), avisos.mensajes());
    }

    @Test
    void flujoCompletoGeneraUnAvisoPorDestinatarioYPorEventoSinDuplicados() {
        String numero = crear(1L);
        servicio.tomar(AGENTE_OBRAS, numero, "Tomar");
        servicio.resolver(AGENTE_OBRAS, numero, "Reparado");
        servicio.reabrir(CIUDADANO, numero, "Persiste");
        servicio.resolver(AGENTE_OBRAS, numero, "Reparado nuevamente");
        servicio.cerrar(CIUDADANO, numero, "Confirmado");

        assertEquals(List.of("ReclamoCreado", "ReclamoAsignado", "EstadoReclamoCambiado", "EstadoReclamoCambiado",
                "ReclamoResuelto", "EstadoReclamoCambiado", "EstadoReclamoCambiado", "ReclamoResuelto",
                "EstadoReclamoCambiado"), eventos.nombres());
        assertEquals(List.of(CIUDADANO, CIUDADANO, AGENTE_OBRAS, CIUDADANO, CIUDADANO, CIUDADANO, AGENTE_OBRAS,
                CIUDADANO, CIUDADANO, AGENTE_OBRAS), avisos.destinatarios());
        assertTrue(avisos.buscarPorDestinatario(AGENTE_VIAL).isEmpty());
        assertEquals(EstadoReclamo.CERRADO, reclamos.buscarPorNumero(numero).orElseThrow().getEstado());
    }

    @Test
    void asignacionManualYReasignacionAvisanAlAgenteDeCadaArea() {
        String numero = crear(CATEGORIA_SIN_AREA);
        assertEquals(List.of(CIUDADANO), avisos.destinatarios());

        servicio.asignar(ADMINISTRADOR, numero, e.area.getId(), "Manual");
        servicio.reasignar(ADMINISTRADOR, numero, e.otraArea.getId(), "Reasignar");
        servicio.cancelar(CIUDADANO, numero, "Cancelar");

        assertEquals(List.of("ReclamoCreado", "ReclamoAsignado", "ReclamoAsignado", "EstadoReclamoCambiado"),
                eventos.nombres());
        assertEquals(List.of(CIUDADANO, CIUDADANO, AGENTE_OBRAS, CIUDADANO, AGENTE_VIAL, CIUDADANO),
                avisos.destinatarios());
        assertTrue(avisos.mensajes().get(3).contains("Vial"));
        assertTrue(avisos.mensajes().get(5).contains("CANCELADO"));
    }

    @Test
    void elVencimientoPublicaReclamoVencidoYAvisaAlCiudadanoYAlArea() {
        String numero = crear(1L);
        var vencimientos = new ServicioVencimientos(reclamos, eventos, Clock.offset(e.reloj, Duration.ofHours(49)));

        assertEquals(1, vencimientos.marcarVencidos());

        Reclamo reclamo = reclamos.buscarPorNumero(numero).orElseThrow();
        assertTrue(reclamo.isVencido());
        assertEquals(Prioridad.MEDIA, reclamo.getPrioridad());
        assertEquals("ReclamoVencido", eventos.nombres().getLast());
        assertEquals(List.of(CIUDADANO, CIUDADANO, AGENTE_OBRAS, CIUDADANO, AGENTE_OBRAS), avisos.destinatarios());

        assertEquals(0, vencimientos.marcarVencidos());
        assertEquals(5, avisos.guardadas.size());
    }
}
