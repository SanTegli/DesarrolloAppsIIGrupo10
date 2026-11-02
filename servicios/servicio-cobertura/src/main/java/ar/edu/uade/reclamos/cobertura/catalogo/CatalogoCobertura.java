package ar.edu.uade.reclamos.cobertura.catalogo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo de cobertura en memoria, leído de {@code cobertura.json} al arrancar. Este servicio es el dueño
 * de la cobertura: el backend le pregunta qué áreas cubren cada combinación y conserva el resto de los
 * datos del área (nombre, si está activa, email).
 *
 * <p>Se valida completo al construirse: un archivo inconsistente impide arrancar en lugar de dar
 * respuestas incorrectas después.
 */
public class CatalogoCobertura {

    private final int version;
    private final Map<Long, DatosCatalogo.Municipio> municipios = new HashMap<>();
    private final Map<Long, Long> municipioDeCadaBarrio = new HashMap<>();
    private final Set<Long> categorias = new HashSet<>();
    private final List<DatosCatalogo.Area> areas;

    public CatalogoCobertura(DatosCatalogo datos) {
        if (datos == null || datos.version() < 1) {
            throw new IllegalStateException("El catálogo de cobertura debe tener version 1 o mayor");
        }
        this.version = datos.version();
        for (DatosCatalogo.Municipio municipio : datos.municipios()) {
            requerirId(municipio.id(), "municipio");
            if (municipios.put(municipio.id(), municipio) != null) {
                throw new IllegalStateException("Municipio repetido en el catálogo: " + municipio.id());
            }
            for (DatosCatalogo.Barrio barrio : municipio.barrios()) {
                requerirId(barrio.id(), "barrio");
                if (municipioDeCadaBarrio.put(barrio.id(), municipio.id()) != null) {
                    throw new IllegalStateException("Barrio repetido en el catálogo: " + barrio.id());
                }
            }
        }
        for (DatosCatalogo.Categoria categoria : datos.categorias()) {
            requerirId(categoria.id(), "categoría");
            if (!categorias.add(categoria.id())) {
                throw new IllegalStateException("Categoría repetida en el catálogo: " + categoria.id());
            }
        }
        Set<Long> idsDeAreas = new HashSet<>();
        for (DatosCatalogo.Area area : datos.areas()) {
            validarArea(area);
            if (!idsDeAreas.add(area.id())) {
                throw new IllegalStateException("Área repetida en el catálogo: " + area.id());
            }
        }
        this.areas = datos.areas().stream().sorted(Comparator.comparingLong(DatosCatalogo.Area::id)).toList();
    }

    /** Lee el JSON del catálogo. Lo usa la configuración con {@code cobertura.json} o el archivo que se indique. */
    public static CatalogoCobertura leer(InputStream json, ObjectMapper mapper) {
        try (json) {
            return new CatalogoCobertura(mapper.readValue(json, DatosCatalogo.class));
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo leer el catálogo de cobertura", error);
        }
    }

    /**
     * Áreas que atienden la categoría y cubren el barrio, ordenadas por id. Una lista vacía significa que
     * ninguna área lo cubre; no es un error.
     *
     * @throws IdentificadorDesconocidoException si algún id no existe o el barrio es de otro municipio
     */
    public List<DatosCatalogo.Area> consultar(long municipioId, long barrioId, long categoriaId) {
        if (!municipios.containsKey(municipioId)) {
            throw new IdentificadorDesconocidoException("No existe el municipio " + municipioId);
        }
        Long municipioDelBarrio = municipioDeCadaBarrio.get(barrioId);
        if (municipioDelBarrio == null) {
            throw new IdentificadorDesconocidoException("No existe el barrio " + barrioId);
        }
        if (municipioDelBarrio != municipioId) {
            throw new IdentificadorDesconocidoException(
                    "El barrio " + barrioId + " no pertenece al municipio " + municipioId);
        }
        if (!categorias.contains(categoriaId)) {
            throw new IdentificadorDesconocidoException("No existe la categoría " + categoriaId);
        }
        return areas.stream()
                .filter(area -> area.municipioId() == municipioId)
                .filter(area -> area.barrios().contains(barrioId))
                .filter(area -> area.categorias().contains(categoriaId))
                .toList();
    }

    public int getVersion() {
        return version;
    }

    public int cantidadDeAreas() {
        return areas.size();
    }

    private void validarArea(DatosCatalogo.Area area) {
        requerirId(area.id(), "área");
        if (area.nombre() == null || area.nombre().isBlank() || area.nombre().length() > 80) {
            throw new IllegalStateException("El área " + area.id() + " necesita un nombre de 1 a 80 caracteres");
        }
        if (!municipios.containsKey(area.municipioId())) {
            throw new IllegalStateException("El área " + area.id() + " es de un municipio que no existe: "
                    + area.municipioId());
        }
        for (Long barrio : area.barrios()) {
            if (barrio == null || !Long.valueOf(area.municipioId()).equals(municipioDeCadaBarrio.get(barrio))) {
                throw new IllegalStateException("El área " + area.id() + " cubre un barrio que no es de su municipio: "
                        + barrio);
            }
        }
        for (Long categoria : area.categorias()) {
            if (categoria == null || !categorias.contains(categoria)) {
                throw new IllegalStateException("El área " + area.id() + " atiende una categoría que no existe: "
                        + categoria);
            }
        }
    }

    private static void requerirId(long id, String que) {
        if (id < 1) {
            throw new IllegalStateException("Id de " + que + " inválido en el catálogo: " + id);
        }
    }
}
