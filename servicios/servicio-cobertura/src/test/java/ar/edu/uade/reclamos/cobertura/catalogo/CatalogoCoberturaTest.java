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
    void bacheEnBernalLoCubrenObrasPublicasYMantenimientoVialOrdenadasPorId() {
        assertThat(catalogo.consultar(1, 2, 2)).extracting(DatosCatalogo.Area::id, DatosCatalogo.Area::nombre)
                .containsExactly(tuple(2L, "Obras Públicas"), tuple(4L, "Mantenimiento Vial"));
    }

    @Test
    void luminariaEnQuilmesCentroLaCubreAlumbrado() {
        assertThat(catalogo.consultar(1, 1, 1)).extracting(DatosCatalogo.Area::id).containsExactly(1L);
    }

    @Test
    void combinacionSinAreasDevuelveListaVaciaSinError() {
        assertThat(catalogo.consultar(1, 3, 1)).isEmpty();
    }

    @Test
    void idsDesconocidosSonErroresDeQuienLlama() {
        assertThatThrownBy(() -> catalogo.consultar(99, 2, 2))
                .isInstanceOf(IdentificadorDesconocidoException.class).hasMessage("No existe el municipio 99");
        assertThatThrownBy(() -> catalogo.consultar(1, 99, 2))
                .isInstanceOf(IdentificadorDesconocidoException.class).hasMessage("No existe el barrio 99");
        assertThatThrownBy(() -> catalogo.consultar(1, 2, 99))
                .isInstanceOf(IdentificadorDesconocidoException.class).hasMessage("No existe la categoría 99");
    }

    @Test
    void barrioDeOtroMunicipioSeRechaza() {
        CatalogoCobertura dosMunicipios = new CatalogoCobertura(new DatosCatalogo(1,
                List.of(municipio(1, 1), municipio(2, 5)), List.of(new DatosCatalogo.Categoria(1, "Bache")),
                List.of(new DatosCatalogo.Area(1, "Obras", 2, List.of(5L), List.of(1L)))));

        assertThatThrownBy(() -> dosMunicipios.consultar(1, 5, 1))
                .isInstanceOf(IdentificadorDesconocidoException.class)
                .hasMessage("El barrio 5 no pertenece al municipio 1");
        assertThat(dosMunicipios.consultar(2, 5, 1)).extracting(DatosCatalogo.Area::id).containsExactly(1L);
    }

    @Test
    void elArchivoDelRepositorioEsLaVersionUnoConCuatroAreas() {
        assertThat(catalogo.getVersion()).isEqualTo(1);
        assertThat(catalogo.cantidadDeAreas()).isEqualTo(4);
    }

    @Test
    void unCatalogoInconsistenteImpideArrancar() {
        DatosCatalogo.Categoria bache = new DatosCatalogo.Categoria(1, "Bache");

        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(0, List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(1, List.of(municipio(1, 1)), List.of(bache),
                List.of(new DatosCatalogo.Area(1, "Obras", 1, List.of(9L), List.of(1L))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("barrio");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(1, List.of(municipio(1, 1)), List.of(bache),
                List.of(new DatosCatalogo.Area(1, "Obras", 1, List.of(1L), List.of(7L))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("categoría");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(1, List.of(municipio(1, 1)), List.of(bache),
                List.of(new DatosCatalogo.Area(1, "Obras", 1, List.of(1L), List.of(1L)),
                        new DatosCatalogo.Area(1, "Otra", 1, List.of(1L), List.of(1L))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("repetida");
        assertThatThrownBy(() -> new CatalogoCobertura(new DatosCatalogo(1, List.of(municipio(1, 1)), List.of(bache),
                List.of(new DatosCatalogo.Area(1, " ", 1, List.of(1L), List.of(1L))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nombre");
    }

    @Test
    void unJsonMalFormadoNoSeCarga() {
        assertThatThrownBy(() -> CatalogoCobertura.leer(
                new ByteArrayInputStream("{\"version\": ".getBytes(StandardCharsets.UTF_8)), new ObjectMapper()))
                .hasMessageContaining("catálogo de cobertura");
    }

    private static DatosCatalogo.Municipio municipio(long id, long barrio) {
        return new DatosCatalogo.Municipio(id, "Municipio " + id,
                List.of(new DatosCatalogo.Barrio(barrio, "Barrio " + barrio)));
    }
}
