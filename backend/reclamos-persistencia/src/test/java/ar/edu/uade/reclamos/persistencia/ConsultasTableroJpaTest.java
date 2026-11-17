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

    private Municipio avellaneda;
    private AreaMunicipal obrasAvellaneda;
    private AgenteMunicipal agenteAvellaneda;
    private Categoria luminaria;

    @Test
    void cuentaLosCreadosDelPeriodoConFiltros() {
        escenario();

        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), null, null)).isEqualTo(5);
        assertThat(tablero.contarCreados(DIA_1.plusDays(2), DIA_1.plusDays(4), null, null)).isEqualTo(3);
        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), municipio.getId(), null)).isEqualTo(4);
        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), null, luminaria.getId())).isEqualTo(1);
        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), avellaneda.getId(), luminaria.getId())).isZero();
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
        assertThat(tablero.contarPorEstado(avellaneda.getId(), null))
                .containsExactly(new ConteoPorEstado(EstadoReclamo.CERRADO, 1L));
        assertThat(tablero.contarPorEstado(municipio.getId(), luminaria.getId()))
                .containsExactly(new ConteoPorEstado(EstadoReclamo.INGRESADO, 1L));
    }

    @Test
    void agrupaPendientesYVencidosPorAreaYMunicipioSegunLaFecha() {
        escenario();

        // Día 7: vencieron el ASIGNADO de Obras (día 6) y el INGRESADO sin área (día 6), no el EN_PROCESO.
        List<FilaPendientes> filas = tablero.pendientesPorAreaYMunicipio(DIA_1.plusDays(7), null);

        assertThat(filas).containsExactly(
                new FilaPendientes(area.getId(), "Obras Públicas", municipio.getId(), "Quilmes", 2L, 1L),
                new FilaPendientes(null, null, municipio.getId(), "Quilmes", 1L, 1L));
        assertThat(tablero.pendientesPorAreaYMunicipio(DIA_1, null))
                .extracting(FilaPendientes::vencidos).containsOnly(0L);
        assertThat(tablero.pendientesPorAreaYMunicipio(DIA_1.plusDays(7), area.getId())).containsExactly(
                new FilaPendientes(area.getId(), "Obras Públicas", municipio.getId(), "Quilmes", 2L, 1L));
    }

    @Test
    void sinReclamosTodoEsCero() {
        prepararDatos();

        assertThat(tablero.contarCreados(DIA_1, DIA_1.plusDays(10), null, null)).isZero();
        assertThat(tablero.contarPorEstado(null, null)).isEmpty();
        assertThat(tablero.pendientesPorAreaYMunicipio(DIA_1, null)).isEmpty();
    }

    private void escenario() {
        prepararDatos();
        avellaneda = municipios.guardar(new Municipio("Avellaneda", "Buenos Aires"));
        obrasAvellaneda = new AreaMunicipal("Obras Públicas", "obras.av@example.org", avellaneda);
        obrasAvellaneda.agregarCategoria(categoria);
        obrasAvellaneda = areas.guardar(obrasAvellaneda);
        agenteAvellaneda = (AgenteMunicipal) usuarios.guardar(new AgenteMunicipal(
                "29777888", "Hugo", "Medina", "hugo@example.org", null, obrasAvellaneda));
        luminaria = categorias.guardar(new Categoria("Luminaria rota", "Farol", 48, Prioridad.MEDIA));

        // Bache: 120 horas de plazo (5 días).
        Reclamo asignado = asignado(crear(categoria, municipio, 0));
        reclamos.guardar(asignado);
        Reclamo enProceso = asignado(crear(categoria, municipio, 2));
        enProceso.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, DIA_1.plusDays(2));
        reclamos.guardar(enProceso);
        Reclamo resuelto = asignado(crear(categoria, municipio, 2));
        resuelto.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, DIA_1.plusDays(2));
        resuelto.cambiarEstado(EstadoReclamo.RESUELTO, agente, null, DIA_1.plusDays(3));
        reclamos.guardar(resuelto);
        // Luminaria: 48 horas. Nadie la cubre: queda INGRESADO.
        reclamos.guardar(crear(luminaria, municipio, 3));
        Reclamo cerrado = crear(categoria, avellaneda, 4);
        cerrado.asignarArea(obrasAvellaneda, null, "Asignación automática", cerrado.getFechaCreacion());
        cerrado.cambiarEstado(EstadoReclamo.EN_PROCESO, agenteAvellaneda, null, DIA_1.plusDays(4));
        cerrado.cambiarEstado(EstadoReclamo.RESUELTO, agenteAvellaneda, null, DIA_1.plusDays(4));
        cerrado.cambiarEstado(EstadoReclamo.CERRADO, ciudadano, null, DIA_1.plusDays(5));
        reclamos.guardar(cerrado);
        flushYLimpiar();
    }

    private Reclamo crear(Categoria categoria, Municipio municipioDelReclamo, int dia) {
        Clock reloj = Clock.fixed(DIA_1.plusDays(dia).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new ReclamoFactory(reloj).crear(ciudadano, categoria, municipioDelReclamo, "Reclamo de prueba",
                "Belgrano 450");
    }

    private Reclamo asignado(Reclamo reclamo) {
        reclamo.asignarArea(area, null, "Asignación automática", reclamo.getFechaCreacion());
        return reclamo;
    }
}
