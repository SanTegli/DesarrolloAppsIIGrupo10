package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.aplicacion.seguridad.PermisosReclamo;
import ar.edu.uade.reclamos.comun.excepcion.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsultaReclamosServiceTest {
    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ReclamoRepository reclamos = mock(ReclamoRepository.class);
    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final ConsultaReclamosService servicio =
            new ConsultaReclamosService(usuarios, reclamos, notificaciones, new PermisosReclamo());

    @BeforeEach
    void prepararUsuarios() {
        for (Usuario usuario : List.of(e.ciudadano, e.otroCiudadano, e.agente, e.otroAgente, e.administrador)) {
            when(usuarios.buscarPorId(usuario.getId())).thenReturn(Optional.of(usuario));
        }
    }

    @Test
    void ciudadanoListaPropiosYFiltraEstadoSinAlterarOrden() {
        Reclamo ingresado = e.ingresado();
        Reclamo asignado = e.asignado();
        when(reclamos.buscarPorCiudadano(1L)).thenReturn(List.of(asignado, ingresado));
        assertThat(servicio.listar(1L, null, null, null)).containsExactly(asignado, ingresado);
        assertThat(servicio.listar(1L, EstadoReclamo.INGRESADO, null, null)).containsExactly(ingresado);
        verify(reclamos, never()).buscarTodos();
        verify(reclamos, never()).buscarPorArea(any());
    }

    @Test
    void agenteListaSoloSuAreaYFiltraEstado() {
        Reclamo asignado = e.asignado();
        when(reclamos.buscarPorArea(1L)).thenReturn(List.of(asignado));
        assertThat(servicio.listar(3L, EstadoReclamo.ASIGNADO, null, null)).containsExactly(asignado);
        assertThat(servicio.listar(3L, EstadoReclamo.INGRESADO, null, null)).isEmpty();
        verify(reclamos, never()).buscarTodos();
        verify(reclamos, never()).buscarPorCiudadano(any());
    }

    @Test
    void administradorListaTodosYCombinaFiltros() {
        Reclamo ingresado = e.ingresado();
        Reclamo asignado = e.asignado();
        Reclamo ajeno = e.factory.crear(e.otroCiudadano, e.categoria, e.barrio, "Pozo", "Calle");
        ajeno.asignarArea(e.area, null, null, java.time.LocalDateTime.now(e.reloj));
        when(reclamos.buscarTodos()).thenReturn(List.of(ingresado, asignado, ajeno));
        when(reclamos.buscarPorArea(1L)).thenReturn(List.of(asignado, ajeno));
        when(reclamos.buscarPorCiudadano(1L)).thenReturn(List.of(ingresado, asignado));
        assertThat(servicio.listar(5L, null, null, null)).containsExactly(ingresado, asignado, ajeno);
        assertThat(servicio.listar(5L, EstadoReclamo.ASIGNADO, 1L, 1L)).containsExactly(asignado);
        assertThat(servicio.listar(5L, EstadoReclamo.INGRESADO, null, 1L)).containsExactly(ingresado);
        assertThat(servicio.listar(5L, EstadoReclamo.INGRESADO, 1L, null)).isEmpty();
    }

    @Test
    void filtrosConIdsInexistentesDevuelvenVacioSinCargarRecursos() {
        when(reclamos.buscarPorArea(99L)).thenReturn(List.of());
        when(reclamos.buscarPorCiudadano(99L)).thenReturn(List.of());
        when(reclamos.buscarPorArea(1L)).thenReturn(List.of(e.asignado()));
        assertThat(servicio.listar(5L, null, 99L, null)).isEmpty();
        assertThat(servicio.listar(5L, null, null, 99L)).isEmpty();
        assertThat(servicio.listar(5L, null, 1L, 99L)).isEmpty();
        verify(usuarios, never()).buscarPorId(99L);
    }

    @Test
    void ciudadanoYAgenteNoPuedenUsarFiltrosAdministrativosAunqueSeanPropios() {
        for (Long usuarioId : List.of(1L, 3L)) {
            assertThatThrownBy(() -> servicio.listar(usuarioId, null, 1L, null))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.listar(usuarioId, null, null, 1L))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(reclamos);
    }

    @Test
    void consultaPorNumeroRespetaVisibilidadParaTodosLosRoles() {
        Reclamo reclamo = e.asignado();
        when(reclamos.buscarPorNumero(reclamo.getNumero())).thenReturn(Optional.of(reclamo));
        for (Long usuarioId : List.of(1L, 3L, 5L)) {
            assertThat(servicio.buscarPorNumero(usuarioId, reclamo.getNumero())).isSameAs(reclamo);
        }
        for (Long usuarioId : List.of(2L, 4L)) {
            assertThatThrownBy(() -> servicio.buscarPorNumero(usuarioId, reclamo.getNumero()))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
    }

    @Test
    void notificacionesExigenMismaVisibilidadQueReclamo() {
        Reclamo reclamo = e.asignado();
        Notificacion aviso = mock(Notificacion.class);
        when(reclamos.buscarPorNumero(reclamo.getNumero())).thenReturn(Optional.of(reclamo));
        when(notificaciones.buscarPorReclamo(reclamo.getNumero())).thenReturn(List.of(aviso));
        for (Long usuarioId : List.of(2L, 4L)) {
            assertThatThrownBy(() -> servicio.consultarNotificaciones(usuarioId, reclamo.getNumero()))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(notificaciones);
        for (Long usuarioId : List.of(1L, 3L, 5L)) {
            assertThat(servicio.consultarNotificaciones(usuarioId, reclamo.getNumero())).containsExactly(aviso);
        }
    }

    @Test
    void accionesUsanPoliticaDominioYVisibilidad() {
        Reclamo reclamo = e.asignado();
        when(reclamos.buscarPorNumero(reclamo.getNumero())).thenReturn(Optional.of(reclamo));
        assertThat(servicio.accionesDisponibles(1L, reclamo.getNumero())).containsExactly(EstadoReclamo.CANCELADO);
        assertThat(servicio.accionesDisponibles(3L, reclamo.getNumero())).containsExactly(EstadoReclamo.EN_PROCESO);
        assertThat(servicio.accionesDisponibles(5L, reclamo.getNumero())).containsExactly(EstadoReclamo.ASIGNADO);
        assertThatThrownBy(() -> servicio.accionesDisponibles(2L, reclamo.getNumero()))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void usuarioOReclamoInexistenteEsRecursoNoEncontrado() {
        assertThatThrownBy(() -> servicio.listar(99L, null, null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.buscarPorNumero(1L, "REC-INEXISTENTE"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.consultarNotificaciones(1L, "REC-INEXISTENTE"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(notificaciones);
    }
}
