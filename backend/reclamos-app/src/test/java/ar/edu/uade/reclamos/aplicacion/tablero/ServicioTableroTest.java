package ar.edu.uade.reclamos.aplicacion.tablero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.modelo.EstadoReclamo;
import ar.edu.uade.reclamos.dominio.reporte.ConsultasTablero;
import ar.edu.uade.reclamos.dominio.reporte.ConteoPorEstado;
import ar.edu.uade.reclamos.dominio.reporte.FilaPendientes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reglas del tablero sin base: período, porcentajes y qué ve cada rol. */
class ServicioTableroTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneOffset.UTC);

    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final Consultas consultas = new Consultas();
    private final ServicioTablero servicio = new ServicioTablero(consultas,
            new DoblesEnMemoria.Usuarios(e.ciudadano, e.agente, e.administrador), new DoblesEnMemoria.Barrios(e.barrio),
            new DoblesEnMemoria.Categorias(e.categoria), RELOJ);

    @Test
    void sinFechasUsaLosUltimosTreintaDiasConHoyIncluido() {
        TableroPublico tablero = servicio.publico(null, null, null, null);

        assertThat(tablero.hasta()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(tablero.desde()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(consultas.desde).isEqualTo(LocalDateTime.of(2026, 10, 5, 0, 0));
        assertThat(consultas.hasta).isEqualTo(LocalDateTime.of(2026, 11, 4, 0, 0));
    }

    @Test
    void sumaLosPendientesYCalculaElPorcentajeDeAtendidos() {
        consultas.estados = List.of(new ConteoPorEstado(EstadoReclamo.INGRESADO, 1L),
                new ConteoPorEstado(EstadoReclamo.ASIGNADO, 2L), new ConteoPorEstado(EstadoReclamo.EN_PROCESO, 3L),
                new ConteoPorEstado(EstadoReclamo.RESUELTO, 2L), new ConteoPorEstado(EstadoReclamo.CERRADO, 1L),
                new ConteoPorEstado(EstadoReclamo.CANCELADO, 4L));
        consultas.creados = 7;

        TableroPublico tablero = servicio.publico(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null);

        assertThat(tablero.creadosEnPeriodo()).isEqualTo(7);
        assertThat(tablero.pendientes()).isEqualTo(6);
        assertThat(tablero.resueltos()).isEqualTo(2);
        assertThat(tablero.cerrados()).isEqualTo(1);
        assertThat(tablero.cancelados()).isEqualTo(4);
        assertThat(tablero.rechazados()).isZero();
        // 3 atendidos sobre 9 (los cancelados no cuentan): 33,3 %
        assertThat(tablero.porcentajeAtendidos()).isEqualTo(33.3);
    }

    @Test
    void sinCasosNoHayPorcentaje() {
        consultas.estados = List.of(new ConteoPorEstado(EstadoReclamo.CANCELADO, 2L));

        assertThat(servicio.publico(null, null, null, null).porcentajeAtendidos()).isNull();
        assertThat(ServicioTablero.porcentaje(0, 0)).isNull();
        assertThat(ServicioTablero.porcentaje(1, 3)).isEqualTo(33.3);
        assertThat(ServicioTablero.porcentaje(2, 3)).isEqualTo(66.7);
        assertThat(ServicioTablero.porcentaje(3, 3)).isEqualTo(100.0);
    }

    @Test
    void elPeriodoTieneQueSerValido() {
        assertThatThrownBy(() -> servicio.publico(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), null, null))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> servicio.publico(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), null, null))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("366");
        assertThat(servicio.publico(LocalDate.of(2025, 1, 2), LocalDate.of(2026, 1, 2), null, null).desde())
                .isEqualTo(LocalDate.of(2025, 1, 2));
        assertThatThrownBy(() -> servicio.publico(null, null, 999L, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.publico(null, null, null, 999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void elAdministradorVeTodasLasAreasYLosSinAsignar() {
        consultas.filas = List.of(new FilaPendientes(e.area.getId(), "Obras", e.barrio.getId(), "Bernal", 4L, 1L),
                new FilaPendientes(null, null, e.barrio.getId(), "Bernal", 2L, 2L));

        TableroGestion tablero = servicio.gestion(e.administrador.getId());

        assertThat(consultas.areaId).isNull();
        assertThat(tablero.pendientes()).isEqualTo(6);
        assertThat(tablero.vencidos()).isEqualTo(3);
        assertThat(tablero.sinAsignar()).isEqualTo(2);
        assertThat(tablero.porcentajeVencidos()).isEqualTo(50.0);
        assertThat(tablero.fecha()).isEqualTo(LocalDateTime.of(2026, 11, 3, 15, 0));
    }

    @Test
    void elAgenteVeSoloSuAreaYUnCiudadanoNada() {
        TableroGestion tablero = servicio.gestion(e.agente.getId());

        assertThat(consultas.areaId).isEqualTo(e.agente.getArea().getId());
        assertThat(tablero.areaId()).isEqualTo(e.agente.getArea().getId());
        assertThat(tablero.sinAsignar()).isNull();
        assertThat(tablero.porcentajeVencidos()).isNull();
        assertThatThrownBy(() -> servicio.gestion(e.ciudadano.getId())).isInstanceOf(AccesoDenegadoException.class);
    }

    /** Doble de las consultas: devuelve lo preparado y anota con qué se la llamó. */
    private static final class Consultas implements ConsultasTablero {
        long creados;
        List<ConteoPorEstado> estados = new ArrayList<>();
        List<FilaPendientes> filas = new ArrayList<>();
        LocalDateTime desde;
        LocalDateTime hasta;
        Long areaId;

        @Override
        public long contarCreados(LocalDateTime desde, LocalDateTime hasta, Long barrioId, Long categoriaId) {
            this.desde = desde;
            this.hasta = hasta;
            return creados;
        }

        @Override
        public List<ConteoPorEstado> contarPorEstado(Long barrioId, Long categoriaId) {
            return estados;
        }

        @Override
        public List<FilaPendientes> pendientesPorAreaYBarrio(LocalDateTime ahora, Long areaId) {
            this.areaId = areaId;
            return filas;
        }
    }
}
