package ar.edu.uade.reclamos.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.dominio.integracion.Geocodificador;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** El Proxy de caché: misma interfaz, menos llamadas a la API externa. */
class GeocodificadorConCacheTest {

    private final Municipio quilmes = municipio("Quilmes", 1L);
    private final Municipio otroMunicipio = municipio("Berazategui", 2L);
    private final List<String> llamadas = new ArrayList<>();
    private final RelojMovible reloj = new RelojMovible();
    private boolean caido;

    private final Geocodificador real = new Geocodificador() {
        @Override
        public List<SugerenciaUbicacion> sugerir(String direccion, Municipio municipio) {
            llamadas.add(direccion);
            if (caido) {
                throw new ServicioExternoNoDisponibleException("Georef no respondió");
            }
            return List.of(new SugerenciaUbicacion(direccion.toUpperCase(), -34.7, -58.3));
        }

        @Override
        public Optional<String> municipioEn(double latitud, double longitud) {
            llamadas.add(latitud + "," + longitud);
            return Optional.of("Quilmes");
        }
    };

    private final GeocodificadorConCache cache = new GeocodificadorConCache(real, Duration.ofMinutes(10), 2, reloj);

    @Test
    void laMismaDireccionEnElMismoMunicipioSeConsultaUnaSolaVez() {
        List<SugerenciaUbicacion> primera = cache.sugerir("Belgrano 450", quilmes);
        List<SugerenciaUbicacion> repetida = cache.sugerir("  belgrano   450 ", quilmes);

        assertThat(repetida).isEqualTo(primera);
        assertThat(llamadas).containsExactly("Belgrano 450");
    }

    @Test
    void otroMunicipioEsOtraConsulta() {
        cache.sugerir("Belgrano 450", quilmes);
        cache.sugerir("Belgrano 450", otroMunicipio);

        assertThat(llamadas).hasSize(2);
    }

    @Test
    void despuesDeLaDuracionVuelveAConsultar() {
        cache.sugerir("Belgrano 450", quilmes);
        reloj.avanzar(Duration.ofMinutes(11));
        cache.sugerir("Belgrano 450", quilmes);

        assertThat(llamadas).hasSize(2);
    }

    @Test
    void lasFallasNoSeGuardan() {
        caido = true;
        assertThatThrownBy(() -> cache.sugerir("Belgrano 450", quilmes))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
        caido = false;

        assertThat(cache.sugerir("Belgrano 450", quilmes)).hasSize(1);
        assertThat(llamadas).hasSize(2);
    }

    @Test
    void guardaComoMaximoLasConsultasConfiguradasYDescartaLaMenosUsada() {
        cache.sugerir("Belgrano 450", quilmes);
        cache.sugerir("Moreno 100", quilmes);
        cache.sugerir("Belgrano 450", quilmes);
        cache.sugerir("Rivadavia 300", quilmes);

        assertThat(cache.cantidadGuardada()).isEqualTo(2);
        cache.sugerir("Belgrano 450", quilmes);
        cache.sugerir("Moreno 100", quilmes);
        assertThat(llamadas).containsExactly("Belgrano 450", "Moreno 100", "Rivadavia 300", "Moreno 100");
    }

    @Test
    void laUbicacionInversaNoSeGuarda() {
        cache.municipioEn(-34.7, -58.3);
        cache.municipioEn(-34.7, -58.3);

        assertThat(llamadas).containsExactly("-34.7,-58.3", "-34.7,-58.3");
        assertThat(cache.cantidadGuardada()).isZero();
    }

    private static Municipio municipio(String nombre, Long id) {
        Municipio municipio = new Municipio(nombre, "Buenos Aires");
        municipio.setId(id);
        return municipio;
    }

    private static final class RelojMovible extends Clock {
        private Instant ahora = Instant.parse("2026-11-02T12:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
