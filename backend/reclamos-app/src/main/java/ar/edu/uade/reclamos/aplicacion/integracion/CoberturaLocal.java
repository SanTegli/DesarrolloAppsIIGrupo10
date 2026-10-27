package ar.edu.uade.reclamos.aplicacion.integracion;

import ar.edu.uade.reclamos.dominio.integracion.CatalogoCobertura;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import java.util.List;

/**
 * Cobertura tomada de la base del backend: las relaciones {@code area_barrio} y {@code area_categoria}.
 * Es la fuente por defecto ({@code reclamos.cobertura.origen: local}) y la de los tests.
 */
public class CoberturaLocal implements CatalogoCobertura {

    private final AreaMunicipalRepository areas;

    public CoberturaLocal(AreaMunicipalRepository areas) {
        this.areas = areas;
    }

    /** La base tiene un solo municipio por área, así que el municipio no hace falta para filtrar. */
    @Override
    public List<AreaMunicipal> buscarCandidatas(Long municipioId, Long barrioId, Long categoriaId) {
        return areas.buscarCandidatas(categoriaId, barrioId);
    }
}
