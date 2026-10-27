package ar.edu.uade.reclamos.dominio.integracion;

import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import java.util.List;

/**
 * Convierte la dirección escrita por el ciudadano en sugerencias con coordenadas. Una dirección puede ser
 * ambigua: el ciudadano elige la sugerencia correcta.
 *
 * <p>En la Segunda Parte la implementa {@code GeorefGeocodificador}; cambiar de proveedor es otra clase.
 */
public interface Geocodificador {

    /**
     * @param barrio aporta el municipio y la provincia para acotar la búsqueda
     * @return hasta unas pocas sugerencias; vacía si no reconoce la dirección
     * @throws ServicioExternoNoDisponibleException si el proveedor no respondió
     */
    List<SugerenciaUbicacion> sugerir(String direccion, Barrio barrio);
}
