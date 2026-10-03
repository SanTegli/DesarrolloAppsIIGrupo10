package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.CanalNotificacion;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServicioNotificacionesTest {
    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final LocalDateTime fecha = LocalDateTime.now(e.reloj);
    private final AgenteMunicipal segundoAgente = conId(new AgenteMunicipal("27555666", "Fabian", "Luna",
            "fabian@example.org", null, e.area), 6L);
    private final DoblesEnMemoria.Reclamos reclamos = new DoblesEnMemoria.Reclamos();
    private final DoblesEnMemoria.Notificaciones avisos = new DoblesEnMemoria.Notificaciones();
    private final DoblesEnMemoria.Usuarios usuarios = new DoblesEnMemoria.Usuarios(
            e.ciudadano, e.otroCiudadano, e.agente, e.otroAgente, e.administrador, segundoAgente);
    private final ServicioNotificaciones servicio = new ServicioNotificaciones(reclamos, avisos, usuarios);

    private static AgenteMunicipal conId(AgenteMunicipal agente, Long id) {
        agente.setId(id);
        return agente;
    }

    private String guardar(Reclamo reclamo) {
        return reclamos.guardar(reclamo).getNumero();
    }

    @Test
    void elIngresoAvisaSoloAlCiudadanoPorElCanalInterno() {
        String numero = guardar(e.ingresado());

        servicio.avisarIngreso(numero, fecha);

        assertEquals(1, avisos.guardadas.size());
        Notificacion aviso = avisos.guardadas.get(0);
        assertSame(e.ciudadano, aviso.getDestinatario());
        assertEquals(CanalNotificacion.INTERNO, aviso.getCanal());
        assertEquals("Tu reclamo " + numero + " fue ingresado.", aviso.getMensaje());
        assertEquals(fecha, aviso.getFechaEnvio());
        assertEquals(numero, aviso.getReclamo().getNumero());
    }

    @Test
    void laAsignacionAvisaAlCiudadanoYATodosLosAgentesActivosDelArea() {
        String numero = guardar(e.asignado());

        servicio.avisarAsignacion(numero, e.area.getId(), e.area.getNombre(), fecha);

        assertEquals(List.of(1L, 3L, 6L), avisos.destinatarios());
        assertEquals(List.of(
                "Tu reclamo " + numero + " fue asignado al area Obras.",
                "El reclamo " + numero + " fue asignado a tu area Obras.",
                "El reclamo " + numero + " fue asignado a tu area Obras."), avisos.mensajes());
    }

    @Test
    void laAsignacionNoAvisaAAgentesInactivosNiDeOtraArea() {
        String numero = guardar(e.asignado());
        segundoAgente.desactivar();

        servicio.avisarAsignacion(numero, e.area.getId(), e.area.getNombre(), fecha);

        assertEquals(List.of(1L, 3L), avisos.destinatarios());
    }

    @Test
    void elCambioAResueltoNoAvisaPorqueLaResolucionTieneSuPropioAviso() {
        String numero = guardar(e.resuelto());

        servicio.avisarCambioDeEstado(numero, EstadoReclamo.EN_PROCESO, EstadoReclamo.RESUELTO, fecha);
        assertTrue(avisos.guardadas.isEmpty());

        servicio.avisarResolucion(numero, fecha);
        assertEquals(List.of(1L), avisos.destinatarios());
        assertTrue(avisos.mensajes().get(0).contains("fue resuelto"));
    }

    @Test
    void reaperturaYCierreAvisanTambienAlAgenteACargo() {
        String numero = guardar(e.resuelto());

        servicio.avisarCambioDeEstado(numero, EstadoReclamo.RESUELTO, EstadoReclamo.EN_PROCESO, fecha);
        servicio.avisarCambioDeEstado(numero, EstadoReclamo.RESUELTO, EstadoReclamo.CERRADO, fecha);

        assertEquals(List.of(1L, 3L, 1L, 3L), avisos.destinatarios());
        assertEquals("El reclamo " + numero + " paso de RESUELTO a CERRADO.", avisos.mensajes().get(3));
    }

    @Test
    void losDemasCambiosDeEstadoAvisanSoloAlCiudadano() {
        String numero = guardar(e.asignado());

        servicio.avisarCambioDeEstado(numero, EstadoReclamo.ASIGNADO, EstadoReclamo.EN_PROCESO, fecha);
        servicio.avisarCambioDeEstado(numero, EstadoReclamo.ASIGNADO, EstadoReclamo.CANCELADO, fecha);

        assertEquals(List.of(1L, 1L), avisos.destinatarios());
    }

    @Test
    void elVencimientoDeUnReclamoTomadoAvisaAlCiudadanoYAlAgenteACargo() {
        Reclamo reclamo = e.asignado();
        reclamo.cambiarEstado(EstadoReclamo.EN_PROCESO, e.agente, null, fecha);
        String numero = guardar(reclamo);

        servicio.avisarVencimiento(numero, Prioridad.MEDIA, fecha);

        assertEquals(List.of(1L, 3L), avisos.destinatarios());
        assertEquals("Tu reclamo " + numero + " supero su fecha limite y paso a prioridad MEDIA.",
                avisos.mensajes().get(0));
        assertEquals("El reclamo " + numero + " vencio sin resolverse. Prioridad MEDIA.", avisos.mensajes().get(1));
    }

    @Test
    void elVencimientoDeUnReclamoSinTomarAvisaATodosLosAgentesDelArea() {
        String numero = guardar(e.asignado());

        servicio.avisarVencimiento(numero, Prioridad.MEDIA, fecha);

        assertEquals(List.of(1L, 3L, 6L), avisos.destinatarios());
    }

    @Test
    void elVencimientoDeUnReclamoSinAreaAvisaSoloAlCiudadano() {
        String numero = guardar(e.ingresado());

        servicio.avisarVencimiento(numero, Prioridad.MEDIA, fecha);

        assertEquals(List.of(1L), avisos.destinatarios());
    }

    @Test
    void unReclamoInexistenteNoGeneraAvisos() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.avisarIngreso("REC-INEXISTENTE", fecha));
        assertTrue(avisos.guardadas.isEmpty());
    }
}
