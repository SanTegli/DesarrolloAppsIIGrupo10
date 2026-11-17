package ar.edu.uade.reclamos.aplicacion.integracion;

import ar.edu.uade.reclamos.dominio.integracion.Geocodificador;
import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * <b>Proxy de caché</b> de un {@link Geocodificador}: tiene la misma interfaz y responde de memoria las
 * consultas que se repiten (la misma dirección en el mismo municipio) durante un rato. Así, buscar dos veces
 * la misma dirección, o buscarla y después crear el reclamo, no vuelve a llamar a la API externa. Si se pasa a
 * Nominatim, su política de uso exige este tipo de caché.
 *
 * <p>Las fallas no se guardan: la próxima consulta vuelve a intentar. Guarda como máximo {@code maximo}
 * consultas y descarta primero la usada hace más tiempo. La ubicación inversa ({@link #municipioEn}) no se
 * guarda: se pide una vez por alta, con las coordenadas que eligió el ciudadano.
 */
public class GeocodificadorConCache implements Geocodificador {

    private record Guardada(List<SugerenciaUbicacion> sugerencias, Instant vence) { }

    private final Geocodificador real;
    private final Duration duracion;
    private final Clock reloj;
    private final Map<String, Guardada> guardadas;

    public GeocodificadorConCache(Geocodificador real, Duration duracion, int maximo, Clock reloj) {
        this.real = real;
        this.duracion = duracion;
        this.reloj = reloj;
        this.guardadas = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Guardada> masVieja) {
                return size() > maximo;
            }
        };
    }

    @Override
    public List<SugerenciaUbicacion> sugerir(String direccion, Municipio municipio) {
        String clave = municipio.getId() + "|"
                + direccion.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        synchronized (guardadas) {
            Guardada guardada = guardadas.get(clave);
            if (guardada != null && reloj.instant().isBefore(guardada.vence())) {
                return guardada.sugerencias();
            }
        }
        List<SugerenciaUbicacion> sugerencias = real.sugerir(direccion, municipio);
        synchronized (guardadas) {
            guardadas.put(clave, new Guardada(List.copyOf(sugerencias), reloj.instant().plus(duracion)));
        }
        return sugerencias;
    }

    @Override
    public Optional<String> municipioEn(double latitud, double longitud) {
        return real.municipioEn(latitud, longitud);
    }

    int cantidadGuardada() {
        synchronized (guardadas) {
            return guardadas.size();
        }
    }
}
