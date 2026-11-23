package ar.edu.uade.reclamos.cobertura.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogoCoberturaTest {

    private final CatalogoCobertura catalogo = CatalogoCobertura.leer(
            getClass().getResourceAsStream("/cobertura.json"), new ObjectMapper());

    @Test
    void bacheEnQuilmesLoCubrenObrasPublicasYMantenimientoVialOrdenadasPorId() {
        assertThat(catalogo.consultar(1, 2)).extracting(DatosCatalogo.Area::id, DatosCatalogo.Area::nombre)
                .containsExactly(tuple(2L, "Obras Públicas"), tuple(4L, "Mantenimiento Vial"));
    }

    @Test
    void cadaMunicipioTieneSusPropiasAreas() {
        assertThat(catalogo.consultar(1, 1)).extracting(DatosCatalogo.Area::id).containsExactly(1L);
        assertThat(catalogo.consultar(3, 1)).extracting(DatosCatalogo.Area::id).containsExactly(7L);
        assertThat(catalogo.consultar(2, 2)).extracting(DatosCatalogo.Area::id).containsExactly(5L);
    }

    @Test
    void combinacionSinAreasDevuelveListaVaciaSinError() {
        // Avellaneda no tiene Alumbrado: la luminaria queda para asignar a mano.
        assertThat(catalogo.consultar(2, 1)).isEmpty();
    }

    @Test
    void idsDesconocidosSonErroresDeQuienLlama() {
        assertThatThrownBy(() -> catalogo.consultar(99, 2))
                .isInstanceOf(IdentificadorDesconocidoException.class).hasMessage("No existe el municipio 99");
        assertThatThrownBy(() -> catalogo.consultar(1, 99))
                .isInstanceOf(IdentificadorDesconocidoException.class).hasMessage("No existe la categoría 99");
    }

    @Test
    void elArchivoDelRepositorioEsLaVersionDosConCoberturaParaLasNuevasJurisdicciones() {
        assertThat(catalogo.getVersion()).isEqualTo(2);
        assertThat(catalogo.cantidadDeAreas()).isEqualTo(18);
        assertThat(catalogo.consultar(4, 1)).extracting(DatosCatalogo.Area::id).containsExactly(10L);
        assertThat(catalogo.consultar(5, 2)).extracting(DatosCatalogo.Area::id).containsExactly(14L);
        assertThat(catalogo.consultar(6, 3)).extracting(DatosCatalogo.Area::id).containsExactly(18L);
    }

    @Test
    void unCatalogoInconsistenteImpideArrancar() {
        DatosCatalogo.Categoria bache = new DatosCatalogo.Categoria(1, "Bache");

        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(0, List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(2, List.of(municipio(1)), List.of(bache),
                List.of(new DatosCatalogo.Area(1, "Obras", 9, List.of(1L))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("municipio");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(2, List.of(municipio(1)), List.of(bache),
                List.of(new DatosCatalogo.Area(1, "Obras", 1, List.of(7L))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("categoría");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(2, List.of(municipio(1)), List.of(bache),
                List.of(new DatosCatalogo.Area(1, "Obras", 1, List.of(1L)),
                        new DatosCatalogo.Area(1, "Otra", 1, List.of(1L))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("repetida");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(2, List.of(municipio(1)), List.of(bache),
                List.of(new DatosCatalogo.Area(1, " ", 1, List.of(1L))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nombre");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(2, List.of(municipio(1), municipio(1)),
                List.of(bache), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Municipio repetido");
    }

    @Test
    void unJsonMalFormadoNoSeCarga() {
        assertThatThrownBy(() -> CatalogoCobertura.leer(
                new ByteArrayInputStream("{\"version\": ".getBytes(StandardCharsets.UTF_8)), new ObjectMapper()))
                .hasMessageContaining("catálogo de cobertura");
    }

    private static DatosCatalogo.Municipio municipio(long id) {
        return new DatosCatalogo.Municipio(id, "Municipio " + id);
    }
}
