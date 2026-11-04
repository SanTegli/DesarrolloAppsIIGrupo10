package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.reporte.ConsultasTablero;
import ar.edu.uade.reclamos.dominio.reporte.ConteoPorEstado;
import ar.edu.uade.reclamos.dominio.reporte.FilaPendientes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Las consultas del tablero contra la base, con un escenario chico contado a mano:
 *
 * <pre>
 * Bache en Bernal (Obras):        creado día 1, ASIGNADO   -> pendiente; vence día 6
 * Bache en Bernal (Obras):        creado día 3, EN_PROCESO -> pendiente; vence día 8
 * Bache en Bernal (Obras):        creado día 3, RESUELTO
 * Luminaria en Bernal, sin área:  creado día 4, INGRESADO  -> pendiente sin área; vence día 6
 * Bache en Ezpeleta (Obras):      creado día 5, CERRADO
 * </pre>
 */
class ConsultasTableroJpaTest extends PersistenciaTestBase {

    private static final LocalDateTime DIA_1 = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Autowired ConsultasTablero tablero;

    private Barrio ezpeleta;
    private Categoria luminaria;

    @Test
    void cuentaLosCreadosDelPeriodoConFiltros() {
        escenario();

        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), null, null)).isEqualTo(5);
        assertThat(tablero.contarCreados(DIA_1.plusDays(2), DIA_1.plusDays(4), null, null)).isEqualTo(3);
        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), barrio.getId(), null)).isEqualTo(4);
        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), null, luminaria.getId())).isEqualTo(1);
        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), ezpeleta.getId(), luminaria.getId())).isZero();
        // El extremo de arriba no entra: [desde, hasta)
        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(2), null, null)).isEqualTo(1);
    }

    @Test
    void cuentaCuantosHayHoyEnCadaEstado() {
        escenario();

        assertThat(tablero.contarPorEstado(null, null)).containsExactly(
                new ConteoPorEstado(EstadoReclamo.INGRESADO, 1L), new ConteoPorEstado(EstadoReclamo.ASIGNADO, 1L),
                new ConteoPorEstado(EstadoReclamo.EN_PROCESO, 1L), new ConteoPorEstado(EstadoReclamo.RESUELTO, 1L),
                new ConteoPorEstado(EstadoReclamo.CERRADO, 1L));
        assertThat(tablero.contarPorEstado(ezpeleta.getId(), null))
                .containsExactly(new ConteoPorEstado(EstadoReclamo.CERRADO, 1L));
        assertThat(tablero.contarPorEstado(barrio.getId(), luminaria.getId()))
                .containsExactly(new ConteoPorEstado(EstadoReclamo.INGRESADO, 1L));
    }

    @Test
    void agrupaPendientesYVencidosPorAreaYBarrioSegunLaFecha() {
        escenario();

        // Día 7: vencieron el ASIGNADO de Obras (día 6) y el INGRESADO sin área (día 6), no el EN_PROCESO.
        List<FilaPendientes> filas = tablero.pendientesPorAreaYBarrio(DIA_1.plusDays(7), null);

        assertThat(filas).containsExactly(
                new FilaPendientes(area.getId(), "Obras Públicas", barrio.getId(), "Bernal", 2L, 1L),
                new FilaPendientes(null, null, barrio.getId(), "Bernal", 1L, 1L));
        assertThat(tablero.pendientesPorAreaYBarrio(DIA_1, null))
                .extracting(FilaPendientes::vencidos).containsOnly(0L);
        assertThat(tablero.pendientesPorAreaYBarrio(DIA_1.plusDays(7), area.getId())).containsExactly(
                new FilaPendientes(area.getId(), "Obras Públicas", barrio.getId(), "Bernal", 2L, 1L));
    }

    @Test
    void sinReclamosTodoEsCero() {
        prepararDatos();

        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), null, null)).isZero();
        assertThat(tablero.contarPorEstado(null, null)).isEmpty();
        assertThat(tablero.pendientesPorAreaYBarrio(DIA_1, null)).isEmpty();
    }

    private void escenario() {
        prepararDatos();
        ezpeleta = barrios.guardar(new Barrio("Ezpeleta", municipio));
        area.agregarBarrio(ezpeleta);
        area = areas.guardar(area);
        luminaria = categorias.guardar(new Categoria("Luminaria rota", "Farol", 48, Prioridad.MEDIA));

        // Bache: 120 horas de plazo (5 días).
        Reclamo asignado = asignado(crear(categoria, barrio, 0));
        reclamos.guardar(asignado);
        Reclamo enProceso = asignado(crear(categoria, barrio, 2));
        enProceso.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, DIA_1.plusDays(2));
        reclamos.guardar(enProceso);
        Reclamo resuelto = asignado(crear(categoria, barrio, 2));
        resuelto.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, DIA_1.plusDays(2));
        resuelto.cambiarEstado(EstadoReclamo.RESUELTO, agente, null, DIA_1.plusDays(3));
        reclamos.guardar(resuelto);
        // Luminaria: 48 horas. Nadie la cubre: queda INGRESADO.
        reclamos.guardar(crear(luminaria, barrio, 3));
        Reclamo cerrado = asignado(crear(categoria, ezpeleta, 4));
        cerrado.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, DIA_1.plusDays(4));
        cerrado.cambiarEstado(EstadoReclamo.RESUELTO, agente, null, DIA_1.plusDays(4));
        cerrado.cambiarEstado(EstadoReclamo.CERRADO, ciudadano, null, DIA_1.plusDays(5));
        reclamos.guardar(cerrado);
        flushYLimpiar();
    }

    private Reclamo crear(Categoria categoria, Barrio barrio, int dia) {
        Clock reloj = Clock.fixed(DIA_1.plusDays(dia).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new ReclamoFactory(reloj).crear(ciudadano, categoria, barrio, "Reclamo de prueba", "Belgrano 450");
    }

    private Reclamo asignado(Reclamo reclamo) {
        reclamo.asignarArea(area, null, "Asignación automática", reclamo.getFechaCreacion());
        return reclamo;
    }
}
