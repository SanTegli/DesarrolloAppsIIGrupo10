package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.aplicacion.seguridad.PermisosReclamo;
import ar.edu.uade.reclamos.comun.excepcion.*;
import ar.edu.uade.reclamos.dominio.estrategia.*;
import ar.edu.uade.reclamos.dominio.evento.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReclamoServiceTest {
    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final BarrioRepository barrios = mock(BarrioRepository.class);
    private final AreaMunicipalRepository areas = mock(AreaMunicipalRepository.class);
    private final ReclamoRepository reclamos = mock(ReclamoRepository.class);
    private final EstrategiaPrioridad prioridad = mock(EstrategiaPrioridad.class);
    private final EstrategiaAsignacion asignacion = mock(EstrategiaAsignacion.class);
    private final PublicadorEventos eventos = mock(PublicadorEventos.class);
    private final ReclamoService servicio = new ReclamoService(usuarios, categorias, barrios, areas,
            reclamos, e.factory, prioridad, asignacion, new PermisosReclamo(), e.reloj, eventos);

    @BeforeEach
    void usuariosYGuardado() {
        for (Usuario usuario : List.of(e.ciudadano, e.otroCiudadano, e.agente, e.otroAgente, e.administrador)) {
            when(usuarios.buscarPorId(usuario.getId())).thenReturn(Optional.of(usuario));
        }
        when(reclamos.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private void prepararCreacion() {
        when(categorias.buscarPorId(1L)).thenReturn(Optional.of(e.categoria));
        when(barrios.buscarPorId(1L)).thenReturn(Optional.of(e.barrio));
        when(prioridad.calcular(any())).thenReturn(Prioridad.CRITICA);
    }

    @Test
    void crearCalculaPrioridadAsignaAutomaticamenteYPersisteEnOrden() {
        prepararCreacion();
        when(areas.buscarCandidatas(1L, 1L)).thenReturn(List.of(e.area));
        when(asignacion.seleccionarArea(any(), eq(List.of(e.area)))).thenReturn(Optional.of(e.area));
        Reclamo resultado = servicio.crear(1L, 1L, 1L, "Pozo peligroso", "Belgrano 450");
        assertThat(resultado.getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
        assertThat(resultado.getArea()).isSameAs(e.area);
        assertThat(resultado.getPrioridad()).isEqualTo(Prioridad.CRITICA);
        assertThat(resultado.getCiudadano()).isSameAs(e.ciudadano);
        assertThat(resultado.getFechaLimite()).isEqualTo(resultado.getFechaCreacion().plusHours(48));
        assertThat(resultado.getHistorial()).hasSize(2);
        assertThat(resultado.getHistorial().get(1).getUsuario()).isNull();
        var orden = inOrder(usuarios, categorias, barrios, prioridad, areas, asignacion, reclamos, eventos);
        orden.verify(usuarios).buscarPorId(1L);
        orden.verify(categorias).buscarPorId(1L);
        orden.verify(barrios).buscarPorId(1L);
        orden.verify(prioridad).calcular(resultado);
        orden.verify(areas).buscarCandidatas(1L, 1L);
        orden.verify(asignacion).seleccionarArea(resultado, List.of(e.area));
        orden.verify(reclamos).guardar(resultado);
        orden.verify(eventos).publicar(new ReclamoCreado(resultado.getNumero(), 1L, 1L, 1L,
                Prioridad.CRITICA, resultado.getFechaCreacion()));
        orden.verify(eventos).publicar(new ReclamoAsignado(resultado.getNumero(), 1L, e.area.getNombre(),
                null, java.time.LocalDateTime.now(e.reloj)));
        verifyNoMoreInteractions(eventos);
    }

    @Test
    void crearSinCandidatasGuardaIngresadoConPrioridadCalculada() {
        prepararCreacion();
        when(areas.buscarCandidatas(1L, 1L)).thenReturn(List.of());
        when(asignacion.seleccionarArea(any(), eq(List.of()))).thenReturn(Optional.empty());
        Reclamo resultado = servicio.crear(1L, 1L, 1L, "Pozo", "Belgrano 450");
        assertThat(resultado.getEstado()).isEqualTo(EstadoReclamo.INGRESADO);
        assertThat(resultado.getArea()).isNull();
        assertThat(resultado.getPrioridad()).isEqualTo(Prioridad.CRITICA);
        assertThat(resultado.getHistorial()).hasSize(1);
        verify(reclamos).guardar(resultado);
        verify(eventos).publicar(new ReclamoCreado(resultado.getNumero(), 1L, 1L, 1L,
                Prioridad.CRITICA, resultado.getFechaCreacion()));
        verifyNoMoreInteractions(eventos);
    }

    @Test
    void ciudadanoInactivoNoPuedeCrearNiSeGuarda() {
        prepararCreacion();
        e.ciudadano.desactivar();
        assertThatThrownBy(() -> servicio.crear(1L, 1L, 1L, "Pozo", "Calle"))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(prioridad, areas, asignacion, reclamos);
    }

    @Test
    void crearSoloPermiteCiudadanosAntesDeCargarCatalogos() {
        for (Long id : List.of(3L, 5L)) {
            assertThatThrownBy(() -> servicio.crear(id, 1L, 1L, "Pozo", "Calle"))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(categorias, barrios, areas, reclamos);
    }

    @Test
    void recursosInexistentesYDatosInvalidosNoSeGuardan() {
        assertThatThrownBy(() -> servicio.crear(99L, 1L, 1L, "Pozo", "Calle"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.crear(1L, 99L, 1L, "Pozo", "Calle"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        prepararCreacion();
        assertThatThrownBy(() -> servicio.crear(1L, 1L, 99L, "Pozo", "Calle"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.crear(1L, 1L, 1L, " ", "Calle"))
                .isInstanceOf(DatosInvalidosException.class);
        verify(reclamos, never()).guardar(any());
    }

    private Reclamo cargar(Reclamo reclamo) {
        when(reclamos.buscarPorNumero(reclamo.getNumero())).thenReturn(Optional.of(reclamo));
        when(areas.buscarPorId(1L)).thenReturn(Optional.of(e.area));
        when(areas.buscarPorId(2L)).thenReturn(Optional.of(e.otraArea));
        return reclamo;
    }

    @Test
    void administradorAsignaManualmente() {
        Reclamo reclamo = cargar(e.ingresado());
        assertThat(servicio.asignar(5L, reclamo.getNumero(), 1L, "Manual")).isSameAs(reclamo);
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
        assertThat(reclamo.getArea()).isSameAs(e.area);
        assertThat(reclamo.getHistorial().get(1).getUsuario()).isSameAs(e.administrador);
        verify(reclamos).guardar(reclamo);
        verify(eventos).publicar(new ReclamoAsignado(reclamo.getNumero(), 1L, e.area.getNombre(),
                5L, java.time.LocalDateTime.now(e.reloj)));
        verifyNoMoreInteractions(eventos);
    }

    @Test
    void administradorReasignaYRegistraHistorial() {
        Reclamo reclamo = cargar(e.asignado());
        servicio.reasignar(5L, reclamo.getNumero(), 2L, "Reasignación");
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
        assertThat(reclamo.getArea()).isSameAs(e.otraArea);
        assertThat(reclamo.getAgente()).isNull();
        assertThat(reclamo.getHistorial()).hasSize(3);
        verify(reclamos).guardar(reclamo);
        verify(eventos).publicar(new ReclamoAsignado(reclamo.getNumero(), 2L, e.otraArea.getNombre(),
                5L, java.time.LocalDateTime.now(e.reloj)));
        verifyNoMoreInteractions(eventos);
    }

    @Test
    void asignacionRechazaRolAreaInactivaMismaAreaYEstadoInvalido() {
        Reclamo reclamo = cargar(e.asignado());
        assertThatThrownBy(() -> servicio.asignar(1L, reclamo.getNumero(), 2L, null))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.asignar(5L, reclamo.getNumero(), 1L, null))
                .isInstanceOf(ReglaNegocioException.class);
        e.otraArea.desactivar();
        assertThatThrownBy(() -> servicio.asignar(5L, reclamo.getNumero(), 2L, null))
                .isInstanceOf(ReglaNegocioException.class);
        Reclamo resuelto = cargar(e.resuelto());
        assertThatThrownBy(() -> servicio.asignar(5L, resuelto.getNumero(), 1L, null))
                .isInstanceOf(ReglaNegocioException.class);
        verify(reclamos, never()).guardar(any());
    }

    @Test
    void agenteTomaYResuelveConHistorialYPersistencia() {
        Reclamo reclamo = cargar(e.asignado());
        servicio.tomar(3L, reclamo.getNumero(), "Cuadrilla");
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.EN_PROCESO);
        assertThat(reclamo.getAgente()).isSameAs(e.agente);
        servicio.resolver(3L, reclamo.getNumero(), "Reparado");
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.RESUELTO);
        assertThat(reclamo.getHistorial()).hasSize(4);
        verify(reclamos, times(2)).guardar(reclamo);
        var orden = inOrder(reclamos, eventos);
        orden.verify(reclamos).guardar(reclamo);
        orden.verify(eventos).publicar(new EstadoReclamoCambiado(reclamo.getNumero(), EstadoReclamo.ASIGNADO,
                EstadoReclamo.EN_PROCESO, 3L, java.time.LocalDateTime.now(e.reloj)));
        orden.verify(reclamos).guardar(reclamo);
        orden.verify(eventos).publicar(new EstadoReclamoCambiado(reclamo.getNumero(), EstadoReclamo.EN_PROCESO,
                EstadoReclamo.RESUELTO, 3L, java.time.LocalDateTime.now(e.reloj)));
        orden.verify(eventos).publicar(new ReclamoResuelto(reclamo.getNumero(), 1L, 3L,
                java.time.LocalDateTime.now(e.reloj)));
        verifyNoMoreInteractions(eventos);
    }

    @Test
    void transicionInexistentePrevaleceSobreRolOPertenencia() {
        Reclamo reclamo = cargar(e.ingresado());
        assertThatThrownBy(() -> servicio.resolver(2L, reclamo.getNumero(), null))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.INGRESADO);
        verify(reclamos, never()).guardar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void transicionValidaRechazaRolYPertenenciaIncorrectos() {
        Reclamo reclamo = cargar(e.asignado());
        assertThatThrownBy(() -> servicio.tomar(5L, reclamo.getNumero(), null))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.tomar(4L, reclamo.getNumero(), null))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.cancelar(2L, reclamo.getNumero(), null))
                .isInstanceOf(AccesoDenegadoException.class);
        verify(reclamos, never()).guardar(any());
    }

    @Test
    void ciudadanoCancelaIngresadoOAsignado() {
        for (Reclamo reclamo : List.of(e.ingresado(), e.asignado())) {
            cargar(reclamo);
            servicio.cancelar(1L, reclamo.getNumero(), "Ya no corresponde");
            assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.CANCELADO);
            verify(reclamos).guardar(reclamo);
        }
    }

    @Test
    void administradorRechazaIngresado() {
        Reclamo reclamo = cargar(e.ingresado());
        servicio.rechazar(5L, reclamo.getNumero(), "No corresponde");
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.RECHAZADO);
        verify(reclamos).guardar(reclamo);
    }

    @Test
    void ciudadanoReabreResuelto() {
        Reclamo reclamo = cargar(e.resuelto());
        servicio.reabrir(1L, reclamo.getNumero(), "Persiste el problema");
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.EN_PROCESO);
        assertThat(reclamo.getAgente()).isSameAs(e.agente);
        verify(reclamos).guardar(reclamo);
    }

    @Test
    void ciudadanoCierraResuelto() {
        Reclamo reclamo = cargar(e.resuelto());
        servicio.cerrar(1L, reclamo.getNumero(), "Confirmado");
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.CERRADO);
        verify(reclamos).guardar(reclamo);
    }

    @Test
    void cambioGenericoNoPermiteAsignadoNiEstadoNulo() {
        Reclamo reclamo = cargar(e.ingresado());
        assertThatThrownBy(() -> servicio.cambiarEstado(5L, reclamo.getNumero(), EstadoReclamo.ASIGNADO, null))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> servicio.cambiarEstado(1L, reclamo.getNumero(), null, null))
                .isInstanceOf(DatosInvalidosException.class);
        verify(reclamos, never()).guardar(any());
    }

    @Test
    void escrituraSobreReclamoOAreaInexistentesFalla() {
        assertThatThrownBy(() -> servicio.cancelar(1L, "REC-INEXISTENTE", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        Reclamo reclamo = cargar(e.ingresado());
        assertThatThrownBy(() -> servicio.asignar(5L, reclamo.getNumero(), 99L, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(reclamos, never()).guardar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void fallaAlGuardarNoPublicaEventos() {
        prepararCreacion();
        when(asignacion.seleccionarArea(any(), any())).thenReturn(Optional.empty());
        when(reclamos.guardar(any())).thenThrow(new ReglaNegocioException("Falla al guardar"));
        assertThatThrownBy(() -> servicio.crear(1L, 1L, 1L, "Pozo", "Calle"))
                .isInstanceOf(ReglaNegocioException.class);
        Reclamo reclamo = cargar(e.asignado());
        assertThatThrownBy(() -> servicio.tomar(3L, reclamo.getNumero(), "Tomar"))
                .isInstanceOf(ReglaNegocioException.class);
        Reclamo ingresado = cargar(e.ingresado());
        assertThatThrownBy(() -> servicio.asignar(5L, ingresado.getNumero(), 1L, "Asignar"))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(eventos);
    }
}
