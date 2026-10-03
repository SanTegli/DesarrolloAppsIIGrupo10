package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.Test;

class AreaMunicipalRepositoryJpaTest extends PersistenciaTestBase {
    @Test void candidatasRequiereCategoriaYBarrioYAreaActiva() {
        prepararDatos();
        Barrio fuera = barrios.guardar(new Barrio("Ezpeleta", municipio));
        Categoria otraCategoria = categorias.guardar(new Categoria("Residuos", "Basura", 72, Prioridad.MEDIA));
        AreaMunicipal segunda = new AreaMunicipal("Mantenimiento Vial", "vial@example.org", municipio);
        segunda.agregarCategoria(categoria);
        segunda.agregarBarrio(barrio);
        segunda = areas.guardar(segunda);
        AreaMunicipal sinBarrio = new AreaMunicipal("Sin barrio", "sinb@example.org", municipio);
        sinBarrio.agregarCategoria(categoria);
        sinBarrio.agregarBarrio(fuera);
        areas.guardar(sinBarrio);
        AreaMunicipal sinCategoria = new AreaMunicipal("Sin categoría", "sinc@example.org", municipio);
        sinCategoria.agregarBarrio(barrio);
        sinCategoria.agregarCategoria(otraCategoria);
        areas.guardar(sinCategoria);
        AreaMunicipal inactiva = new AreaMunicipal("Inactiva", "inactiva@example.org", municipio);
        inactiva.agregarBarrio(barrio);
        inactiva.agregarCategoria(categoria);
        inactiva.desactivar();
        areas.guardar(inactiva);
        // Varias relaciones no deben multiplicar las candidatas.
        area.agregarBarrio(fuera);
        area.agregarCategoria(otraCategoria);
        areas.guardar(area);
        flushYLimpiar();

        assertThat(areas.buscarCandidatas(categoria.getId(), barrio.getId()))
                .extracting(AreaMunicipal::getId).containsExactly(area.getId(), segunda.getId());
        assertThat(areas.buscarCandidatas(otraCategoria.getId(), fuera.getId()))
                .extracting(AreaMunicipal::getId).containsExactly(area.getId());
        assertThat(areas.buscarCandidatas(-1L, barrio.getId())).isEmpty();
        assertThat(areas.buscarCandidatas(categoria.getId(), -1L)).isEmpty();
        AreaMunicipal recuperada = areas.buscarPorId(area.getId()).orElseThrow();
        assertThat(recuperada.getBarrios()).hasSize(2);
        assertThat(recuperada.getCategorias()).hasSize(2);
        assertThat(recuperada.puedeAtender(categoria, barrio)).isTrue();
        assertThat(areas.buscarTodas()).extracting(AreaMunicipal::getNombre).isSorted();
    }
}

