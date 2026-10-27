package ar.edu.uade.reclamos.dominio.integracion;

import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import java.util.List;

/**
 * Qué áreas municipales atienden una categoría en un barrio. Es la entrada de
 * {@code EstrategiaAsignacion}: la estrategia elige entre las áreas que devuelve este catálogo.
 *
 * <p>Implementaciones: {@code CoberturaLocal}, que consulta la base del backend, y en la Segunda Parte
 * {@code CoberturaSoap}, que consulta el servicio SOAP de cobertura.
 */
public interface CatalogoCobertura {

    /**
     * @return áreas activas que atienden la categoría y cubren el barrio, ordenadas por id; vacía si
     *         ninguna lo cubre
     * @throws ServicioExternoNoDisponibleException si la fuente de cobertura no respondió; no es lo
     *         mismo que una lista vacía
     */
    List<AreaMunicipal> buscarCandidatas(Long municipioId, Long barrioId, Long categoriaId);
}
