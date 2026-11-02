package ar.edu.uade.reclamos.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

/**
 * El Adapter de Georef contra respuestas reales grabadas ({@code src/test/resources/georef}). Ningún test
 * llama a Georef: {@link MockRestServiceServer} contesta en su lugar.
 */
class GeorefGeocodificadorTest {

    private final Barrio bernal = new Barrio("Bernal", new Municipio("Quilmes", "Buenos Aires"));

    private MockRestServiceServer georef;
    private GeorefGeocodificador geocodificador;

    @BeforeEach
    void preparar() {
        RestClient.Builder http = RestClient.builder().baseUrl("https://georef.test/georef/api/v2.0");
        georef = MockRestServiceServer.bindTo(http).build();
        geocodificador = new GeorefGeocodificador(http.build(), 10);
    }

    @AfterEach
    void verificar() {
        georef.verify();
    }

    @Test
    void direccionAmbiguaDevuelveTodasLasInterpretacionesConYSinCoordenadas() {
        georef.expect(method(HttpMethod.GET))
                .andExpect(consulta("/georef/api/v2.0/direcciones",
                        "direccion=Belgrano 450&departamento=Quilmes&provincia=Buenos Aires&max=10"))
                .andRespond(withSuccess(new ClassPathResource("georef/belgrano-450.json"), MediaType.APPLICATION_JSON));

        assertThat(geocodificador.sugerir("Belgrano 450", bernal)).containsExactly(
                new SugerenciaUbicacion("GRL BELGRANO 450, Quilmes Oeste, Quilmes, Buenos Aires", null, null),
                new SugerenciaUbicacion("GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires", -34.73614578,
                        -58.322899857500005));
    }

    @Test
    void direccionInexistenteDevuelveListaVacia() {
        georef.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(new ClassPathResource("georef/sin-resultados.json"),
                        MediaType.APPLICATION_JSON));

        assertThat(geocodificador.sugerir("Calle Inventada Xyzw 123", bernal)).isEmpty();
    }

    @Test
    void losCaracteresEspecialesNoRompenLaConsulta() {
        georef.expect(request -> assertThat(request.getURI().getRawQuery())
                        .startsWith("direccion=Belgrano%20%26%20Moreno&"))
                .andRespond(withSuccess(new ClassPathResource("georef/sin-resultados.json"),
                        MediaType.APPLICATION_JSON));

        assertThat(geocodificador.sugerir("Belgrano & Moreno", bernal)).isEmpty();
    }

    @Test
    void direccionQueGeorefNoPuedeInterpretarEsComoNoEncontrarla() {
        georef.expect(method(HttpMethod.GET)).andRespond(withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"errores\":[{\"mensaje\":\"El parámetro direccion es inválido\"}]}"));

        assertThat(geocodificador.sugerir("450", bernal)).isEmpty();
    }

    @Test
    void errorDeGeorefEsServicioNoDisponible() {
        georef.expect(method(HttpMethod.GET)).andRespond(withServerError());

        assertThatThrownBy(() -> geocodificador.sugerir("Belgrano 450", bernal))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    void respuestaConOtraFormaEsServicioNoDisponible() {
        georef.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"resultado\": \"otra cosa\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geocodificador.sugerir("Belgrano 450", bernal))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    void georefQueNoContestaSeCortaEnElTiempoLimite() throws IOException {
        try (ServerSocket mudo = new ServerSocket(0)) {
            GeorefGeocodificador conTiempoCorto = GeorefGeocodificador.crear(
                    "http://localhost:" + mudo.getLocalPort(), Duration.ofMillis(300), Duration.ofMillis(300), 10);
            long inicio = System.nanoTime();

            assertThatThrownBy(() -> conTiempoCorto.sugerir("Belgrano 450", bernal))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
        }
    }

    /** Ruta y parámetros ya decodificados, para no depender de cómo se codifican los espacios. */
    private static RequestMatcher consulta(String ruta, String parametros) {
        return request -> {
            assertThat(request.getURI().getPath()).isEqualTo(ruta);
            assertThat(URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                    .isEqualTo(parametros);
        };
    }
}
