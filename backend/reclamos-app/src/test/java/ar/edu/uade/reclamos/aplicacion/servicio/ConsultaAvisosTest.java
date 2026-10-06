package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.aplicacion.seguridad.PermisosReclamo;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.modelo.Notificacion;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bandeja de avisos: cada usuario ve solo lo que se le notificó a él. */
class ConsultaAvisosTest {
    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final LocalDateTime fecha = LocalDateTime.now(e.reloj);
    private final DoblesEnMemoria.Reclamos reclamos = new DoblesEnMemoria.Reclamos();
    private final DoblesEnMemoria.Notificaciones avisos = new DoblesEnMemoria.Notificaciones();
    private final DoblesEnMemoria.Usuarios usuarios = new DoblesEnMemoria.Usuarios(
            e.ciudadano, e.otroCiudadano, e.agente, e.otroAgente, e.administrador);
    private final ServicioNotificaciones notificaciones = new ServicioNotificaciones(reclamos, avisos, usuarios);
    private final ConsultaReclamosService consultas =
            new ConsultaReclamosService(usuarios, reclamos, avisos, new PermisosReclamo());

    private List<String> mensajesDe(Long usuarioId) {
        return consultas.consultarAvisosDelUsuario(usuarioId).stream().map(Notificacion::getMensaje).toList();
    }

    @Test
    void cadaUsuarioVeSoloSusAvisos() {
        Reclamo reclamo = reclamos.guardar(e.asignado());
        String numero = reclamo.getNumero();
        notificaciones.avisarIngreso(numero, fecha);
        notificaciones.avisarAsignacion(numero, e.area.getId(), e.area.getNombre(), fecha.plusSeconds(1));

        assertEquals(2, consultas.consultarAvisosDelUsuario(e.ciudadano.getId()).size());
        assertEquals(List.of("El reclamo " + numero + " fue asignado a tu area Obras."), mensajesDe(e.agente.getId()));
        assertTrue(consultas.consultarAvisosDelUsuario(e.otroAgente.getId()).isEmpty());
        assertTrue(consultas.consultarAvisosDelUsuario(e.otroCiudadano.getId()).isEmpty());
        assertTrue(consultas.consultarAvisosDelUsuario(e.administrador.getId()).isEmpty());
    }

    @Test
    void losAvisosVienenDelMasNuevoAlMasViejo() {
        String numero = reclamos.guardar(e.asignado()).getNumero();
        notificaciones.avisarIngreso(numero, fecha);
        notificaciones.avisarAsignacion(numero, e.area.getId(), e.area.getNombre(), fecha.plusMinutes(1));
        notificaciones.avisarResolucion(numero, fecha.plusMinutes(2));

        List<String> mensajes = mensajesDe(e.ciudadano.getId());

        assertEquals(3, mensajes.size());
        assertTrue(mensajes.get(0).contains("fue resuelto"), mensajes.get(0));
        assertTrue(mensajes.get(2).contains("fue ingresado"), mensajes.get(2));
    }

    @Test
    void elAvisoConservaElNumeroDelReclamoParaPoderAbrirlo() {
        String numero = reclamos.guardar(e.asignado()).getNumero();
        notificaciones.avisarIngreso(numero, fecha);

        Notificacion aviso = consultas.consultarAvisosDelUsuario(e.ciudadano.getId()).get(0);

        assertEquals(numero, aviso.getReclamo().getNumero());
    }

    @Test
    void unUsuarioInexistenteNoTieneBandeja() {
        assertThrows(RecursoNoEncontradoException.class, () -> consultas.consultarAvisosDelUsuario(999L));
    }
}
