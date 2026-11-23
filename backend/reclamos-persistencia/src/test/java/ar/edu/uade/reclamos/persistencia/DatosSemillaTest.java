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
        assertThat(jdbc.update("delete from area_categoria where area_id = 1 and categoria_id = 1")).isEqualTo(1);
        assertThat(jdbc.update("delete from usuarios where id = 7")).isEqualTo(1);

        ResourceDatabasePopulator semilla = new ResourceDatabasePopulator(new ClassPathResource("db/datos-dev.sql"));
        semilla.setSqlScriptEncoding("UTF-8");
        semilla.execute(jdbc.getDataSource());
        flushYLimpiar();

        assertThat(jdbc.queryForObject("select count(*) from area_categoria where area_id = 1 and categoria_id = 1",
                Integer.class)).isZero();
        assertThat(usuarios.buscarPorId(7L)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from inicializaciones where identificador = 'datos-dev-v2'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inicializaciones where identificador = 'categorias-pr26'",
                Integer.class)).isEqualTo(1);
    }

    @Test void cargaDosVecesSinDuplicarYRespetaLosIdsDelContrato() {
        flushYLimpiar();
        assertThat(municipios.buscarTodos()).extracting(Municipio::getNombre, Municipio::getProvincia)
                .containsExactly(tuple("Quilmes", "Buenos Aires"), tuple("Avellaneda", "Buenos Aires"),
                        tuple("Berazategui", "Buenos Aires"), tuple("Comuna 13", "Ciudad Autónoma de Buenos Aires"),
                        tuple("Comuna 14", "Ciudad Autónoma de Buenos Aires"), tuple("San Isidro", "Buenos Aires"));
        assertThat(categorias.buscarPorId(1L).orElseThrow().getSlaHoras()).isEqualTo(48);
        assertThat(categorias.buscarPorId(2L).orElseThrow().getSlaHoras()).isEqualTo(120);
        assertThat(categorias.buscarPorId(3L).orElseThrow().getSlaHoras()).isEqualTo(72);
        assertThat(categorias.buscarPorId(1L).orElseThrow().getPrioridadBase()).isEqualTo(Prioridad.MEDIA);
        assertThat(categorias.buscarPorId(2L).orElseThrow().getPrioridadBase()).isEqualTo(Prioridad.BAJA);
        assertThat(categorias.buscarPorId(3L).orElseThrow().getPrioridadBase()).isEqualTo(Prioridad.MEDIA);
        // PR 26: cinco categorías más; solo "Otro problema" es "otra", y no la atiende ningún área.
        // En orden alfabético, pero dónde cae "Árbol" depende de la intercalación de la base.
        assertThat(categorias.buscarTodas()).extracting(Categoria::getNombre).containsExactlyInAnyOrder(
                "Luminaria rota", "Bache", "Residuos", "Semáforo averiado", "Árbol caído o con riesgo", "Pérdida de agua",
                "Vereda rota", "Otro problema");
        assertThat(categorias.buscarTodas()).filteredOn(Categoria::esOtra).extracting(Categoria::getId)
                .containsExactly(8L);
        assertThat(categorias.buscarPorId(4L).orElseThrow().getSlaHoras()).isEqualTo(24);
        assertThat(categorias.buscarPorId(7L).orElseThrow().getPrioridadBase()).isEqualTo(Prioridad.BAJA);
        assertThat(areas.buscarTodas()).hasSize(18);
        assertThat(areas.buscarPorId(5L).orElseThrow().getMunicipio().getNombre()).isEqualTo("Avellaneda");
        assertThat(usuarios.buscarTodos()).extracting(Usuario::getId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(usuarios.buscarTodos()).extracting(Usuario::getNombreCompleto)
                .containsExactly("Ana Pérez", "Bruno Díaz", "Carla Gómez", "Diego Sosa",
                        "Fabián Luna", "Gabriela Paz", "Elena Ruiz", "Hugo Medina");
        assertThat(usuarios.buscarPorId(1L).orElseThrow()).isInstanceOf(Ciudadano.class);
        assertThat(usuarios.buscarPorId(2L).orElseThrow()).isInstanceOf(Ciudadano.class);
        assertThat(usuarios.buscarPorId(7L).orElseThrow()).isInstanceOf(Administrador.class);
        for (long areaId = 1; areaId <= 4; areaId++) {
            assertThat(usuarios.buscarAgentesPorArea(areaId)).extracting(AgenteMunicipal::getId)
                    .containsExactly(areaId + 2);
        }
        assertThat(usuarios.buscarAgentesPorArea(5L)).extracting(AgenteMunicipal::getId).containsExactly(8L);
        // (categoría, municipio): Quilmes tiene dos áreas para baches y Avellaneda no tiene Alumbrado.
        assertThat(areas.buscarCandidatas(1L, 1L)).extracting(AreaMunicipal::getId).containsExactly(1L);
        assertThat(areas.buscarCandidatas(2L, 1L)).extracting(AreaMunicipal::getId).containsExactly(2L, 4L);
        assertThat(areas.buscarCandidatas(2L, 2L)).extracting(AreaMunicipal::getId).containsExactly(5L);
        assertThat(areas.buscarCandidatas(1L, 2L)).isEmpty();
        assertThat(areas.buscarCandidatas(1L, 3L)).extracting(AreaMunicipal::getId).containsExactly(7L);
        assertThat(areas.buscarCandidatas(3L, 3L)).extracting(AreaMunicipal::getId).containsExactly(9L);
        assertThat(areas.buscarCandidatas(4L, 2L)).isEmpty();
        assertThat(areas.buscarCandidatas(7L, 1L)).extracting(AreaMunicipal::getId).containsExactly(2L, 4L);
        assertThat(areas.buscarCandidatas(8L, 1L)).isEmpty();
        assertThat(areas.buscarCandidatas(1L, 4L)).extracting(AreaMunicipal::getId).containsExactly(10L);
        assertThat(areas.buscarCandidatas(2L, 5L)).extracting(AreaMunicipal::getId).containsExactly(14L);
        assertThat(areas.buscarCandidatas(3L, 6L)).extracting(AreaMunicipal::getId).containsExactly(18L);
        assertThat(jdbc.queryForObject("select count(*) from area_categoria", Integer.class)).isEqualTo(42);
        assertThat(reclamos.buscarTodos()).isEmpty();
        assertThat(usuarios.buscarTodos()).allMatch(Usuario::tieneContrasena);
        assertThat(usuarios.buscarPorEmail("ana@example.org")).isPresent();
        assertThat(usuarios.buscarPorTelefonoVerificado("+5491100000001").orElseThrow().getId()).isEqualTo(1L);
        assertThat(usuarios.buscarPorTelefonoVerificado("+5491100000002").orElseThrow().getId()).isEqualTo(2L);
        assertThat(usuarios.buscarTodos()).noneMatch(Usuario::isBloqueado);
    }
}
