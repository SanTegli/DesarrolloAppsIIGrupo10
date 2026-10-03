package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConsultaCatalogosServiceTest {
    @Test
    void consultasDeleganSinRequerirIdentidadNiExcluirInactivos() {
        var e = new EscenarioAplicacion();
        var usuarios = mock(UsuarioRepository.class);
        var categorias = mock(CategoriaRepository.class);
        var barrios = mock(BarrioRepository.class);
        var areas = mock(AreaMunicipalRepository.class);
        var servicio = new ConsultaCatalogosService(usuarios, categorias, barrios, areas);
        e.ciudadano.desactivar();
        e.area.desactivar();
        when(usuarios.buscarTodos()).thenReturn(List.of(e.ciudadano, e.agente, e.administrador));
        when(usuarios.buscarPorId(1L)).thenReturn(Optional.of(e.ciudadano));
        when(categorias.buscarTodas()).thenReturn(List.of(e.categoria));
        when(barrios.buscarTodos()).thenReturn(List.of(e.barrio));
        when(areas.buscarTodas()).thenReturn(List.of(e.area));
        assertThat(servicio.usuarios()).containsExactly(e.ciudadano, e.agente, e.administrador);
        assertThat(servicio.usuario(1L)).isSameAs(e.ciudadano);
        assertThat(servicio.categorias()).containsExactly(e.categoria);
        assertThat(servicio.barrios()).containsExactly(e.barrio);
        assertThat(servicio.areas()).containsExactly(e.area);
        assertThatThrownBy(() -> servicio.usuario(99L)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(usuarios).buscarTodos();
        verify(categorias).buscarTodas();
        verify(barrios).buscarTodos();
        verify(areas).buscarTodas();
    }
}
