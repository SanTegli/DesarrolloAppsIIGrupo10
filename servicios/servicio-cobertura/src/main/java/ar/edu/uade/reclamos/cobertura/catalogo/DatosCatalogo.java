package ar.edu.uade.reclamos.cobertura.catalogo;

import java.util.List;

/**
 * Contenido de {@code cobertura.json}: municipios, categorías y qué categorías atiende cada área en su municipio.
 * Los ids son los de la semilla del backend ({@code datos-dev.sql}); {@code CatalogoSincronizadoTest}
 * comprueba que no se desincronicen.
 */
public record DatosCatalogo(int version, List<Municipio> municipios, List<Categoria> categorias, List<Area> areas) {

    public DatosCatalogo {
        municipios = copiar(municipios);
        categorias = copiar(categorias);
        areas = copiar(areas);
    }

    public record Municipio(long id, String nombre) { }

    public record Categoria(long id, String nombre) { }

    /** Un área municipal: su municipio, que es su jurisdicción, y las categorías que atiende. */
    public record Area(long id, String nombre, long municipioId, List<Long> categorias) {
        public Area {
            categorias = copiar(categorias);
        }
    }

    private static <T> List<T> copiar(List<T> lista) {
        return lista == null ? List.of() : List.copyOf(lista);
    }
}
