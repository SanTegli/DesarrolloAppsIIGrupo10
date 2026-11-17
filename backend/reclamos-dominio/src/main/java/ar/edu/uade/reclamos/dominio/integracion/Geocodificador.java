package ar.edu.uade.reclamos.dominio.integracion;

import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import java.util.List;
import java.util.Optional;

/**
 * Convierte la dirección escrita por el ciudadano en sugerencias con coordenadas, y unas coordenadas en el
 * municipio donde caen. Una dirección puede ser ambigua: el ciudadano elige la sugerencia correcta.
 *
 * <p>En la Segunda Parte la implementa {@code GeorefGeocodificador}; cambiar de proveedor es otra clase.
 */
public interface Geocodificador {

    /**
     * @param municipio acota la búsqueda a ese municipio y su provincia
     * @return hasta unas pocas sugerencias; vacía si no reconoce la dirección
     * @throws ServicioExternoNoDisponibleException si el proveedor no respondió
     */
    List<SugerenciaUbicacion> sugerir(String direccion, Municipio municipio);

    /**
     * Ubicación inversa: en qué municipio cae el punto. Sirve para confirmar que la ubicación elegida es del
     * municipio del reclamo.
     *
     * @return el nombre del municipio (partido o departamento); vacío si el punto no cae en ninguno
     * @throws ServicioExternoNoDisponibleException si el proveedor no respondió
     */
    Optional<String> municipioEn(double latitud, double longitud);
}
