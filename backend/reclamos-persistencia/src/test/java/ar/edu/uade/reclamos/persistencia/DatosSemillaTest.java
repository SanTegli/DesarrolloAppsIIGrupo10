package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@Sql({"/db/datos-dev.sql", "/db/datos-dev.sql"})
// El DDL del script puede confirmar transacciones: aislar la base y recrearla por test.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:semilla;MODE=MySQL;DB_CLOSE_DELAY=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DatosSemillaTest extends PersistenciaTestBase {
    @Autowired JdbcTemplate jdbc;

    @Test void noRestauraDatosNiRelacionesEliminadosDespuesDeInicializar() {
        assertThat(jdbc.update("delete from area_barrio where area_id = 1 and barrio_id = 1")).isEqualTo(1);
        assertThat(jdbc.update("delete from area_categoria where area_id = 1 and categoria_id = 1")).isEqualTo(1);
        assertThat(jdbc.update("delete from usuarios where id = 7")).isEqualTo(1);

        ResourceDatabasePopulator semilla = new ResourceDatabasePopulator(new ClassPathResource("db/datos-dev.sql"));
        semilla.setSqlScriptEncoding("UTF-8");
        semilla.execute(jdbc.getDataSource());
        flushYLimpiar();

        assertThat(jdbc.queryForObject("select count(*) from area_barrio where area_id = 1 and barrio_id = 1",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from area_categoria where area_id = 1 and categoria_id = 1",
                Integer.class)).isZero();
        assertThat(usuarios.buscarPorId(7L)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from inicializaciones where identificador = 'datos-dev-v1'",
                Integer.class)).isEqualTo(1);
    }

    @Test void cargaDosVecesSinDuplicarYRespetaLosIdsDelContrato() {
        flushYLimpiar();
        assertThat(municipios.buscarTodos()).hasSize(1);
        assertThat(municipios.buscarPorId(1L).orElseThrow().getProvincia()).isEqualTo("Buenos Aires");
        assertThat(barrios.buscarPorId(1L).orElseThrow().getNombre()).isEqualTo("Quilmes Centro");
        assertThat(barrios.buscarPorId(2L).orElseThrow().getNombre()).isEqualTo("Bernal");
        assertThat(barrios.buscarPorId(3L).orElseThrow().getNombre()).isEqualTo("Ezpeleta");
        assertThat(categorias.buscarPorId(1L).orElseThrow().getSlaHoras()).isEqualTo(48);
        assertThat(categorias.buscarPorId(2L).orElseThrow().getSlaHoras()).isEqualTo(120);
        assertThat(categorias.buscarPorId(3L).orElseThrow().getSlaHoras()).isEqualTo(72);
        assertThat(categorias.buscarPorId(1L).orElseThrow().getPrioridadBase()).isEqualTo(Prioridad.MEDIA);
        assertThat(categorias.buscarPorId(2L).orElseThrow().getPrioridadBase()).isEqualTo(Prioridad.BAJA);
        assertThat(categorias.buscarPorId(3L).orElseThrow().getPrioridadBase()).isEqualTo(Prioridad.MEDIA);
        assertThat(areas.buscarTodas()).hasSize(4);
        assertThat(usuarios.buscarTodos()).extracting(Usuario::getId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(usuarios.buscarTodos()).extracting(Usuario::getNombreCompleto)
                .containsExactly("Ana Pérez", "Bruno Díaz", "Carla Gómez", "Diego Sosa",
                        "Fabián Luna", "Gabriela Paz", "Elena Ruiz");
        assertThat(usuarios.buscarPorId(1L).orElseThrow()).isInstanceOf(Ciudadano.class);
        assertThat(usuarios.buscarPorId(2L).orElseThrow()).isInstanceOf(Ciudadano.class);
        assertThat(usuarios.buscarPorId(7L).orElseThrow()).isInstanceOf(Administrador.class);
        for (long areaId = 1; areaId <= 4; areaId++) {
            assertThat(usuarios.buscarAgentesPorArea(areaId)).extracting(AgenteMunicipal::getId)
                    .containsExactly(areaId + 2);
        }
        assertThat(areas.buscarCandidatas(1L, 2L)).extracting(AreaMunicipal::getId).containsExactly(1L);
        assertThat(areas.buscarCandidatas(2L, 2L)).extracting(AreaMunicipal::getId).containsExactly(2L, 4L);
        assertThat(areas.buscarCandidatas(2L, 3L)).extracting(AreaMunicipal::getId).containsExactly(2L);
        assertThat(areas.buscarCandidatas(1L, 3L)).isEmpty();
        assertThat(areas.buscarCandidatas(3L, 3L)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from area_barrio", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from area_categoria", Integer.class)).isEqualTo(4);
        assertThat(reclamos.buscarTodos()).isEmpty();
    }
}
