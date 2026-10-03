package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CatalogosYUsuariosRepositoryJpaTest extends PersistenciaTestBase {
    @Test void recuperaCatalogosYFiltraBarriosPorMunicipio() {
        prepararDatos();
        Municipio otro = municipios.guardar(new Municipio("Berazategui", "Buenos Aires"));
        barrios.guardar(new Barrio("Centro", otro));
        Barrio anterior = barrios.guardar(new Barrio("Avellaneda", municipio));
        categorias.guardar(new Categoria("Alumbrado", null, 48, Prioridad.MEDIA));
        flushYLimpiar();

        assertThat(municipios.buscarPorId(municipio.getId()).orElseThrow().getNombre()).isEqualTo("Quilmes");
        assertThat(municipios.buscarTodos()).hasSize(2);
        assertThat(barrios.buscarPorId(barrio.getId()).orElseThrow().getMunicipio()).isEqualTo(municipio);
        assertThat(barrios.buscarPorMunicipio(municipio.getId())).extracting(Barrio::getId)
                .containsExactly(anterior.getId(), barrio.getId());
        assertThat(barrios.buscarTodos()).extracting(Barrio::getNombre).isSorted();
        assertThat(categorias.buscarPorId(categoria.getId()).orElseThrow().getPrioridadBase()).isEqualTo(Prioridad.BAJA);
        assertThat(categorias.buscarTodas()).extracting(Categoria::getNombre).containsExactly("Alumbrado", "Bache");
        assertThat(municipios.buscarPorId(-1L)).isEmpty();
        assertThat(barrios.buscarPorId(-1L)).isEmpty();
        assertThat(categorias.buscarPorId(-1L)).isEmpty();
        assertThat(areas.buscarPorId(-1L)).isEmpty();
    }

    @Test void conservaSubtiposYDniYDevuelveSoloAgentesActivosDelArea() {
        prepararDatos();
        Administrador admin = (Administrador) usuarios.guardar(
                new Administrador("20555666", "Elena", "Ruiz", "elena@example.org", null));
        AgenteMunicipal inactivo = new AgenteMunicipal("26444555", "Diego", "Sosa", "diego@example.org", null, area);
        inactivo.desactivar();
        usuarios.guardar(inactivo);
        AreaMunicipal otraArea = areas.guardar(new AreaMunicipal("Otra", "otra@example.org", municipio));
        usuarios.guardar(new AgenteMunicipal("27555666", "Fabian", "Luna", "fabian@example.org", null, otraArea));
        flushYLimpiar();

        assertThat(usuarios.buscarPorId(ciudadano.getId()).orElseThrow()).isInstanceOf(Ciudadano.class);
        assertThat(usuarios.buscarPorId(admin.getId()).orElseThrow().getRol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(usuarios.buscarPorDni(agente.getDni()).orElseThrow()).isInstanceOf(AgenteMunicipal.class);
        assertThat(usuarios.buscarTodos()).hasSize(5);
        assertThat(usuarios.buscarAgentesPorArea(area.getId())).extracting(AgenteMunicipal::getId)
                .containsExactly(agente.getId());
        assertThat(usuarios.buscarPorDni("99999999")).isEmpty();
        assertThat(usuarios.buscarPorId(-1L)).isEmpty();
        assertThat(usuarios.buscarAgentesPorArea(-1L)).isEmpty();
    }

    @Test void dniEsUnicoEntreTodosLosRoles() {
        prepararDatos();
        em.flush();
        assertThatThrownBy(() -> {
            usuarios.guardar(new Administrador(ciudadano.getDni(), "Otro", "Usuario", "otro@example.org", null));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}

