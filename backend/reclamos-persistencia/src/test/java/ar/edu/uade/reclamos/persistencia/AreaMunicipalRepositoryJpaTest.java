package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.Test;

class AreaMunicipalRepositoryJpaTest extends PersistenciaTestBase {
    @Test void candidatasRequiereCategoriaMunicipioYAreaActiva() {
        prepararDatos();
        Municipio avellaneda = municipios.guardar(new Municipio("Avellaneda", "Buenos Aires"));
        Categoria otraCategoria = categorias.guardar(new Categoria("Residuos", "Basura", 72, Prioridad.MEDIA));
        AreaMunicipal segunda = new AreaMunicipal("Mantenimiento Vial", "vial@example.org", municipio);
        segunda.agregarCategoria(categoria);
        segunda = areas.guardar(segunda);
        AreaMunicipal deOtroMunicipio = new AreaMunicipal("Obras Públicas", "obras.av@example.org", avellaneda);
        deOtroMunicipio.agregarCategoria(categoria);
        deOtroMunicipio = areas.guardar(deOtroMunicipio);
        AreaMunicipal sinCategoria = new AreaMunicipal("Sin categoría", "sinc@example.org", municipio);
        sinCategoria.agregarCategoria(otraCategoria);
        areas.guardar(sinCategoria);
        AreaMunicipal inactiva = new AreaMunicipal("Inactiva", "inactiva@example.org", municipio);
        inactiva.agregarCategoria(categoria);
        inactiva.desactivar();
        areas.guardar(inactiva);
        // Varias categorías no deben multiplicar las candidatas.
        area.agregarCategoria(otraCategoria);
        areas.guardar(area);
        flushYLimpiar();

        assertThat(areas.buscarCandidatas(categoria.getId(), municipio.getId()))
                .extracting(AreaMunicipal::getId).containsExactly(area.getId(), segunda.getId());
        assertThat(areas.buscarCandidatas(categoria.getId(), avellaneda.getId()))
                .extracting(AreaMunicipal::getId).containsExactly(deOtroMunicipio.getId());
        assertThat(areas.buscarCandidatas(otraCategoria.getId(), avellaneda.getId())).isEmpty();
        assertThat(areas.buscarCandidatas(-1L, municipio.getId())).isEmpty();
        assertThat(areas.buscarCandidatas(categoria.getId(), -1L)).isEmpty();
        AreaMunicipal recuperada = areas.buscarPorId(area.getId()).orElseThrow();
        assertThat(recuperada.getCategorias()).hasSize(2);
        assertThat(recuperada.puedeAtender(categoria, municipio)).isTrue();
        assertThat(recuperada.puedeAtender(categoria, avellaneda)).isFalse();
        assertThat(areas.buscarTodas()).extracting(AreaMunicipal::getNombre).isSorted();
    }
}
