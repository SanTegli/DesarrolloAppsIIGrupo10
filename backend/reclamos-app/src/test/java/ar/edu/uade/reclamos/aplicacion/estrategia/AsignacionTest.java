package ar.edu.uade.reclamos.aplicacion.estrategia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsignacionTest {
    private final ReclamoRepository repositorio = mock(ReclamoRepository.class);
    private final Reclamo reclamo = PrioridadTest.reclamo(Prioridad.BAJA, "Pozo en la calle");
    private final AreaMunicipal primera = area(2L);
    private final AreaMunicipal segunda = area(4L);

    @Test
    void jurisdiccionSeleccionaMenorIdAunqueLasCandidatasVenganDesordenadas() {
        assertThat(new AsignacionPorJurisdiccion().seleccionarArea(reclamo, List.of(segunda, primera)))
                .contains(primera);
        assertThat(reclamo.getArea()).isNull();
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.INGRESADO);
    }

    @Test
    void ambasEstrategiasAceptanAusenciaDeCandidatas() {
        assertThat(new AsignacionPorJurisdiccion().seleccionarArea(reclamo, List.of())).isEmpty();
        assertThat(new AsignacionPorCargaDeTrabajo(repositorio).seleccionarArea(reclamo, List.of())).isEmpty();
        verifyNoInteractions(repositorio);
    }

    @Test
    void cargaSeleccionaMenosPendientesConsultandoUnaVezPorCandidata() {
        when(repositorio.contarPendientesPorArea(2L)).thenReturn(5L);
        when(repositorio.contarPendientesPorArea(4L)).thenReturn(1L);
        assertThat(new AsignacionPorCargaDeTrabajo(repositorio)
                .seleccionarArea(reclamo, List.of(primera, segunda))).contains(segunda);
        verify(repositorio).contarPendientesPorArea(2L);
        verify(repositorio).contarPendientesPorArea(4L);
        verifyNoMoreInteractions(repositorio);
        assertThat(reclamo.getArea()).isNull();
    }

    @Test
    void cargaDesempataPorIdIncluyendoAreasSinPendientes() {
        when(repositorio.contarPendientesPorArea(2L)).thenReturn(0L);
        when(repositorio.contarPendientesPorArea(4L)).thenReturn(0L);
        assertThat(new AsignacionPorCargaDeTrabajo(repositorio)
                .seleccionarArea(reclamo, List.of(segunda, primera))).contains(primera);
    }

    @Test
    void ambasSeleccionanLaUnicaCandidataSinConsultarRepositorio() {
        assertThat(new AsignacionPorJurisdiccion().seleccionarArea(reclamo, List.of(segunda))).contains(segunda);
        assertThat(new AsignacionPorCargaDeTrabajo(repositorio)
                .seleccionarArea(reclamo, List.of(segunda))).contains(segunda);
        verifyNoInteractions(repositorio);
    }

    private AreaMunicipal area(Long id) {
        AreaMunicipal area = new AreaMunicipal("Área " + id, "area@example.org", reclamo.getBarrio().getMunicipio());
        area.setId(id);
        area.agregarCategoria(reclamo.getCategoria());
        area.agregarBarrio(reclamo.getBarrio());
        return area;
    }
}
