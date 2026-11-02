package ar.edu.uade.reclamos.aplicacion.integracion;

import ar.edu.uade.reclamos.dominio.integracion.Geocodificador;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Geocodificador sobre <a href="https://www.argentina.gob.ar/georef">Georef</a>, la API oficial de
 * normalización de direcciones de Argentina. Es gratis y no usa clave.
 *
 * <p>Es un <b>Adapter</b> y una <b>capa anticorrupción</b>: arma la consulta en el formato de Georef
 * ({@code GET /direcciones?direccion=…&departamento=…&provincia=…}) y traduce su respuesta, con calles,
 * localidades, ids censales y demás, a {@link SugerenciaUbicacion} del dominio. Nada del modelo de Georef sale
 * de esta clase. Cambiar a Nominatim u otro proveedor es escribir otra implementación de
 * {@link Geocodificador}.
 *
 * <p>Solo manda la dirección escrita por el ciudadano, el municipio y la provincia: ningún dato personal.
 */
public class GeorefGeocodificador implements Geocodificador {

    static final String RUTA = "/direcciones";
    private static final Logger LOGGER = LoggerFactory.getLogger(GeorefGeocodificador.class);

    private final RestClient http;
    private final int maximo;

    public GeorefGeocodificador(RestClient http, int maximo) {
        this.http = http;
        this.maximo = maximo;
    }

    /**
     * @param url    URL base, por ejemplo {@code https://apis.datos.gob.ar/georef/api/v2.0}
     * @param maximo cuántos resultados se piden; muchos vienen sin coordenadas
     */
    public static GeorefGeocodificador crear(String url, Duration conexion, Duration respuesta, int maximo) {
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout((int) conexion.toMillis());
        tiempos.setReadTimeout((int) respuesta.toMillis());
        return new GeorefGeocodificador(RestClient.builder().baseUrl(url).requestFactory(tiempos).build(), maximo);
    }

    @Override
    public List<SugerenciaUbicacion> sugerir(String direccion, Barrio barrio) {
        Municipio municipio = barrio.getMunicipio();
        JsonNode respuesta;
        try {
            // Los valores van como variables de la URI para que se codifiquen enteros (un "&" no corta la consulta).
            respuesta = http.get()
                    .uri(uri -> uri.path(RUTA)
                            .queryParam("direccion", "{direccion}")
                            .queryParam("departamento", "{departamento}")
                            .queryParam("provincia", "{provincia}")
                            .queryParam("max", maximo)
                            .build(direccion, municipio.getNombre(), municipio.getProvincia()))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.BadRequest rechazo) {
            // Georef responde 400 cuando no puede interpretar la dirección: para el ciudadano es "no encontrada".
            LOGGER.info("Georef no pudo interpretar una dirección de {} caracteres", direccion.length());
            return List.of();
        } catch (RestClientException error) {
            throw new ServicioExternoNoDisponibleException("Georef no respondió", error);
        }
        if (respuesta == null || !respuesta.path("direcciones").isArray()) {
            throw new ServicioExternoNoDisponibleException("Georef respondió algo que no es una lista de direcciones");
        }
        List<SugerenciaUbicacion> sugerencias = new ArrayList<>();
        for (JsonNode resultado : respuesta.get("direcciones")) {
            String nomenclatura = resultado.path("nomenclatura").asText("").strip();
            if (!nomenclatura.isEmpty()) {
                sugerencias.add(new SugerenciaUbicacion(nomenclatura, numero(resultado.at("/ubicacion/lat")),
                        numero(resultado.at("/ubicacion/lon"))));
            }
        }
        return List.copyOf(sugerencias);
    }

    /** Georef devuelve {@code null} cuando conoce la calle pero no tiene coordenadas para esa altura. */
    private static Double numero(JsonNode valor) {
        return valor.isNumber() ? valor.asDouble() : null;
    }
}
