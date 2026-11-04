package ar.edu.uade.reclamos.aplicacion.facade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.aplicacion.servicio.*;
import ar.edu.uade.reclamos.aplicacion.verificacion.CodigoEmitido;
import ar.edu.uade.reclamos.aplicacion.verificacion.ServicioVerificacionTelefono;
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
    private final ServicioUbicaciones ubicaciones = mock(ServicioUbicaciones.class);
    private final ServicioCuentas cuentas = mock(ServicioCuentas.class);
    private final ServicioVerificacionTelefono verificacion = mock(ServicioVerificacionTelefono.class);
    private final GestionReclamosFacade facade = new GestionReclamosFacade(escritura, consultas, catalogos,
            ubicaciones, cuentas, verificacion);

    @Test
    void crearDelegaParametrosYResultadoSinOtrasInteracciones() {
        var resultado = reclamo;
        when(escritura.crear(1L, 2L, 3L, "Pozo", "Calle")).thenReturn(resultado);
        assertThat(facade.crear(1L, 2L, 3L, "Pozo", "Calle")).isSameAs(resultado);
        verify(escritura).crear(1L, 2L, 3L, "Pozo", "Calle");
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void crearConUbicacionLaValidaYDespuesCreaElReclamo() {
        var elegida = new UbicacionElegida(-34.736, -58.323, "GRL BELGRANO 450, Bernal Oeste, Quilmes");
        var confirmada = Ubicacion.confirmada(-34.736, -58.323, "GRL BELGRANO 450, Bernal Oeste, Quilmes", "georef");
        when(ubicaciones.confirmar(3L, elegida)).thenReturn(confirmada);
        when(escritura.crear(1L, 2L, 3L, "Pozo", "Calle", confirmada)).thenReturn(reclamo);

        assertThat(facade.crear(1L, 2L, 3L, "Pozo", "Calle", elegida)).isSameAs(reclamo);
        var orden = inOrder(ubicaciones, escritura);
        orden.verify(ubicaciones).confirmar(3L, elegida);
        orden.verify(escritura).crear(1L, 2L, 3L, "Pozo", "Calle", confirmada);
        verifyNoMoreInteractions(escritura, consultas, catalogos, ubicaciones);
    }

    @Test
    void sugerirUbicacionesDelegaEnElServicio() {
        var resultado = SugerenciasUbicacion.noDisponible();
        when(ubicaciones.sugerir("Belgrano 450", 3L)).thenReturn(resultado);

        assertThat(facade.sugerirUbicaciones("Belgrano 450", 3L)).isSameAs(resultado);
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
        when(catalogos.usuarios(7L)).thenReturn(resultado);
        assertThat(facade.usuarios(7L)).isSameAs(resultado);
        verify(catalogos).usuarios(7L);
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
    void usuarioConsultadoPorOtroDelegaConQuienConsulta() {
        var resultado = e.ciudadano;
        when(catalogos.usuario(7L, 1L)).thenReturn(resultado);
        assertThat(facade.usuario(7L, 1L)).isSameAs(resultado);
        verify(catalogos).usuario(7L, 1L);
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void verificacionYBloqueoDeleganEnSusServicios() {
        var codigo = new CodigoEmitido("RU4K7P2Q", null, java.time.LocalDateTime.of(2026, 1, 1, 0, 10), "simulado");
        when(verificacion.iniciar(1L)).thenReturn(codigo);
        when(verificacion.confirmarSimulado(1L, "RU4K7P2Q", "+5491155550000")).thenReturn(e.ciudadano);
        when(cuentas.cambiarBloqueo(5L, 1L, true)).thenReturn(e.ciudadano);

        assertThat(facade.iniciarVerificacionTelefono(1L)).isSameAs(codigo);
        assertThat(facade.confirmarVerificacionSimulada(1L, "RU4K7P2Q", "+5491155550000")).isSameAs(e.ciudadano);
        assertThat(facade.cambiarBloqueo(5L, 1L, true)).isSameAs(e.ciudadano);
        verifyNoMoreInteractions(escritura, consultas, catalogos);
    }

    @Test
    void registrarCiudadanoDelegaEnLasCuentas() {
        var resultado = e.ciudadano;
        when(cuentas.registrarCiudadano("30111222", "Ana", "Pérez", "ana@example.org", "secreta123"))
                .thenReturn(resultado);
        assertThat(facade.registrarCiudadano("30111222", "Ana", "Pérez", "ana@example.org", "secreta123"))
                .isSameAs(resultado);
        verify(cuentas).registrarCiudadano("30111222", "Ana", "Pérez", "ana@example.org", "secreta123");
        verifyNoMoreInteractions(escritura, consultas, catalogos, cuentas);
    }

    @Test
    void conservaExcepcionDelServicio() {
        var error = new ReglaNegocioException("Transición inválida");
        when(escritura.cancelar(1L, "REC-12345678", null)).thenThrow(error);
        assertThatThrownBy(() -> facade.cancelar(1L, "REC-12345678", null)).isSameAs(error);
    }
}
