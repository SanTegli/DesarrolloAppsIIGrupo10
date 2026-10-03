package ar.edu.uade.reclamos.aplicacion.facade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.aplicacion.servicio.*;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.modelo.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GestionReclamosFacadeTest {
    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final Reclamo reclamo = e.ingresado();
    private final Notificacion aviso = mock(Notificacion.class);
    private final ReclamoService escritura = mock(ReclamoService.class);
    private final ConsultaReclamosService consultas = mock(ConsultaReclamosService.class);
    private final ConsultaCatalogosService catalogos = mock(ConsultaCatalogosService.class);
    private final GestionReclamosFacade facade = new GestionReclamosFacade(escritura, consultas, catalogos);

    @Test
    void crearDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.crear(1L, 2L, 3L, "Pozo", "Calle")).thenReturn(resultado);
        assertThat(facade.crear(1L, 2L, 3L, "Pozo", "Calle")).isSameAs(resultado);
        verify(escritura).crear(1L, 2L, 3L, "Pozo", "Calle");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void asignarDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.asignar(1L, "REC-12345678", 2L, "Observación")).thenReturn(resultado);
        assertThat(facade.asignar(1L, "REC-12345678", 2L, "Observación")).isSameAs(resultado);
        verify(escritura).asignar(1L, "REC-12345678", 2L, "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void reasignarDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.reasignar(1L, "REC-12345678", 2L, "Observación")).thenReturn(resultado);
        assertThat(facade.reasignar(1L, "REC-12345678", 2L, "Observación")).isSameAs(resultado);
        verify(escritura).reasignar(1L, "REC-12345678", 2L, "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void cambiarEstadoDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.cambiarEstado(1L, "REC-12345678", EstadoReclamo.CANCELADO, "Observación")).thenReturn(resultado);
        assertThat(facade.cambiarEstado(1L, "REC-12345678", EstadoReclamo.CANCELADO, "Observación")).isSameAs(resultado);
        verify(escritura).cambiarEstado(1L, "REC-12345678", EstadoReclamo.CANCELADO, "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void tomarDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.tomar(1L, "REC-12345678", "Observación")).thenReturn(resultado);
        assertThat(facade.tomar(1L, "REC-12345678", "Observación")).isSameAs(resultado);
        verify(escritura).tomar(1L, "REC-12345678", "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void resolverDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.resolver(1L, "REC-12345678", "Observación")).thenReturn(resultado);
        assertThat(facade.resolver(1L, "REC-12345678", "Observación")).isSameAs(resultado);
        verify(escritura).resolver(1L, "REC-12345678", "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void cerrarDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.cerrar(1L, "REC-12345678", "Observación")).thenReturn(resultado);
        assertThat(facade.cerrar(1L, "REC-12345678", "Observación")).isSameAs(resultado);
        verify(escritura).cerrar(1L, "REC-12345678", "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void reabrirDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.reabrir(1L, "REC-12345678", "Observación")).thenReturn(resultado);
        assertThat(facade.reabrir(1L, "REC-12345678", "Observación")).isSameAs(resultado);
        verify(escritura).reabrir(1L, "REC-12345678", "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void rechazarDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.rechazar(1L, "REC-12345678", "Observación")).thenReturn(resultado);
        assertThat(facade.rechazar(1L, "REC-12345678", "Observación")).isSameAs(resultado);
        verify(escritura).rechazar(1L, "REC-12345678", "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void cancelarDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.cancelar(1L, "REC-12345678", "Observación")).thenReturn(resultado);
        assertThat(facade.cancelar(1L, "REC-12345678", "Observación")).isSameAs(resultado);
        verify(escritura).cancelar(1L, "REC-12345678", "Observación");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void listarDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = List.of(reclamo);
        when(consultas.listar(1L, EstadoReclamo.ASIGNADO, 2L, 3L)).thenReturn(resultado);
        assertThat(facade.listar(1L, EstadoReclamo.ASIGNADO, 2L, 3L)).isSameAs(resultado);
        verify(consultas).listar(1L, EstadoReclamo.ASIGNADO, 2L, 3L);
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void buscarPorNumeroDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(consultas.buscarPorNumero(1L, "REC-12345678")).thenReturn(resultado);
        assertThat(facade.buscarPorNumero(1L, "REC-12345678")).isSameAs(resultado);
        verify(consultas).buscarPorNumero(1L, "REC-12345678");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void consultarNotificacionesDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = List.of(aviso);
        when(consultas.consultarNotificaciones(1L, "REC-12345678")).thenReturn(resultado);
        assertThat(facade.consultarNotificaciones(1L, "REC-12345678")).isSameAs(resultado);
        verify(consultas).consultarNotificaciones(1L, "REC-12345678");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void accionesDisponiblesDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = Set.of(EstadoReclamo.CANCELADO);
        when(consultas.accionesDisponibles(1L, "REC-12345678")).thenReturn(resultado);
        assertThat(facade.accionesDisponibles(1L, "REC-12345678")).isSameAs(resultado);
        verify(consultas).accionesDisponibles(1L, "REC-12345678");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void usuariosDelegaParametrosYResultadoSinOtrasInteracciones() {
        List<Usuario> resultado = List.of(e.ciudadano);
        when(catalogos.usuarios()).thenReturn(resultado);
        assertThat(facade.usuarios()).isSameAs(resultado);
        verify(catalogos).usuarios();
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void categoriasDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = List.of(e.categoria);
        when(catalogos.categorias()).thenReturn(resultado);
        assertThat(facade.categorias()).isSameAs(resultado);
        verify(catalogos).categorias();
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void barriosDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = List.of(e.barrio);
        when(catalogos.barrios()).thenReturn(resultado);
        assertThat(facade.barrios()).isSameAs(resultado);
        verify(catalogos).barrios();
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void areasDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = List.of(e.area);
        when(catalogos.areas()).thenReturn(resultado);
        assertThat(facade.areas()).isSameAs(resultado);
        verify(catalogos).areas();
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void usuarioDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = e.ciudadano;
        when(catalogos.usuario(1L)).thenReturn(resultado);
        assertThat(facade.usuario(1L)).isSameAs(resultado);
        verify(catalogos).usuario(1L);
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void conservaExcepcionDelServicio() {
        var error = new ReglaNegocioException("Transición inválida");
        when(escritura.cancelar(1L, "REC-12345678", null)).thenThrow(error);
        assertThatThrownBy(() -> facade.cancelar(1L, "REC-12345678", null)).isSameAs(error);
    }
}
