package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ReclamoRepositoryJpaTest extends PersistenciaTestBase {
    @BeforeEach void preparar() { prepararDatos(); }

    @Test void guardaYRecuperaPorNumeroConSusRelaciones() {
        Reclamo nuevo = nuevoReclamo();
        nuevo.asignarArea(area, null, "Automática", AHORA);
        nuevo.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, "Tomado", AHORA);
        Long id = reclamos.guardar(nuevo).getId();
        flushYLimpiar();

        Reclamo recuperado = reclamos.buscarPorNumero(nuevo.getNumero()).orElseThrow();
        assertThat(recuperado).isNotSameAs(nuevo);
        assertThat(recuperado.getId()).isEqualTo(id);
        assertThat(recuperado.getCiudadano().getDni()).isEqualTo("30111222");
        assertThat(recuperado.getBarrio().getMunicipio().getNombre()).isEqualTo("Quilmes");
        assertThat(recuperado.getCategoria().getSlaHoras()).isEqualTo(120);
        assertThat(recuperado.getArea().getId()).isEqualTo(area.getId());
        assertThat(recuperado.getAgente().getId()).isEqualTo(agente.getId());
        assertThat(recuperado.getEstado()).isEqualTo(EstadoReclamo.EN_PROCESO);
        assertThat(recuperado.getHistorial()).extracting(HistorialEstado::getEstadoNuevo)
                .containsExactly(EstadoReclamo.INGRESADO, EstadoReclamo.ASIGNADO, EstadoReclamo.EN_PROCESO);
        assertThat(recuperado.getHistorial()).allSatisfy(h -> {
            assertThat(h.getId()).isNotNull();
            assertThat(h.getReclamo()).isEqualTo(recuperado);
        });
        assertThat(recuperado.getHistorial().get(1).getUsuario()).isNull();
        assertThat(recuperado.getHistorial().get(0).getUsuario().getRol()).isEqualTo(Rol.CIUDADANO);
        assertThat(reclamos.buscarPorNumero("REC-INEXISTENTE")).isEmpty();
    }

    @Test void permiteAreaYAgenteNulosEnElIngreso() {
        Reclamo guardado = reclamos.guardar(nuevoReclamo());
        flushYLimpiar();
        Reclamo recuperado = reclamos.buscarPorNumero(guardado.getNumero()).orElseThrow();
        assertThat(recuperado.getArea()).isNull();
        assertThat(recuperado.getAgente()).isNull();
        assertThat(recuperado.getHistorial().get(0).getEstadoAnterior()).isNull();
    }

    @Test void actualizaUnReclamoSeparadoDelContextoSinDuplicarSuHistorial() {
        Reclamo inicial = reclamos.guardar(nuevoReclamo());
        flushYLimpiar();
        Reclamo separado = reclamos.buscarPorNumero(inicial.getNumero()).orElseThrow();
        flushYLimpiar();
        separado.asignarArea(area, null, "Asignado", AHORA.plusHours(1));
        reclamos.guardar(separado);
        flushYLimpiar();
        Reclamo actualizado = reclamos.buscarPorNumero(inicial.getNumero()).orElseThrow();
        assertThat(actualizado.getId()).isEqualTo(inicial.getId());
        assertThat(actualizado.getHistorial()).hasSize(2);
        assertThat(actualizado.getArea().getId()).isEqualTo(area.getId());
        assertThat(usuarios.buscarTodos()).hasSize(2);
        assertThat(areas.buscarTodas()).hasSize(1);
    }

    @Test void filtraPorCiudadanoYAreaYOrdenaDelMasNuevoAlMasViejo() {
        Reclamo primero = reclamos.guardar(nuevoReclamo());
        primero.asignarArea(area, null, null, AHORA);
        Reclamo segundo = new Reclamo("REC-00000002", ciudadano, categoria, barrio, "Otro pozo",
                "Calle 2", Prioridad.BAJA, AHORA.plusHours(1), AHORA.plusHours(121));
        segundo = reclamos.guardar(segundo);
        Ciudadano otro = (Ciudadano) usuarios.guardar(
                new Ciudadano("31222333", "Bruno", "Díaz", "bruno@example.org", null));
        reclamos.guardar(new Reclamo("REC-00000003", otro, categoria, barrio, "Otro",
                "Calle 3", Prioridad.MEDIA, AHORA.plusHours(2), AHORA.plusHours(3)));
        flushYLimpiar();

        assertThat(reclamos.buscarPorCiudadano(ciudadano.getId())).extracting(Reclamo::getNumero)
                .containsExactly(segundo.getNumero(), primero.getNumero());
        assertThat(reclamos.buscarPorArea(area.getId())).extracting(Reclamo::getNumero)
                .containsExactly(primero.getNumero());
        assertThat(reclamos.buscarTodos()).extracting(Reclamo::getNumero)
                .containsExactly("REC-00000003", segundo.getNumero(), primero.getNumero());
        assertThat(reclamos.buscarPorCiudadano(-1L)).isEmpty();
        assertThat(reclamos.buscarPorArea(-1L)).isEmpty();
    }

    @Test void cuentaPendientesYBuscaVencidosConLosEstadosDelContrato() {
        Reclamo ingresado = reclamos.guardar(nuevoReclamo());
        Reclamo asignado = nuevoReclamo();
        asignado.asignarArea(area, null, null, AHORA);
        reclamos.guardar(asignado);
        Reclamo enProceso = nuevoReclamo();
        enProceso.asignarArea(area, null, null, AHORA);
        enProceso.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, AHORA);
        reclamos.guardar(enProceso);
        Reclamo resuelto = nuevoReclamo();
        resuelto.asignarArea(area, null, null, AHORA);
        resuelto.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, null, AHORA);
        resuelto.cambiarEstado(EstadoReclamo.RESUELTO, agente, null, AHORA);
        reclamos.guardar(resuelto);
        Reclamo cancelado = nuevoReclamo();
        cancelado.cambiarEstado(EstadoReclamo.CANCELADO, ciudadano, null, AHORA);
        reclamos.guardar(cancelado);
        Reclamo marcado = nuevoReclamo();
        marcado.marcarVencido(AHORA.plusHours(121));
        reclamos.guardar(marcado);
        flushYLimpiar();

        assertThat(reclamos.contarPendientesPorArea(area.getId())).isEqualTo(2);
        assertThat(reclamos.contarPendientesPorArea(-1L)).isZero();
        assertThat(reclamos.buscarVencidosSinMarcar(AHORA.plusHours(120))).isEmpty();
        assertThat(reclamos.buscarVencidosSinMarcar(AHORA.plusHours(121)))
                .extracting(Reclamo::getNumero)
                .containsExactly(enProceso.getNumero(), asignado.getNumero(), ingresado.getNumero());
    }

    @Test void elNumeroEsUnicoEnLaBase() {
        Reclamo primero = reclamos.guardar(nuevoReclamo());
        em.flush();
        Reclamo duplicado = new Reclamo(primero.getNumero(), ciudadano, categoria, barrio, "Duplicado",
                "Calle", Prioridad.BAJA, AHORA, AHORA.plusHours(1));
        assertThatThrownBy(() -> { reclamos.guardar(duplicado); em.flush(); })
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

