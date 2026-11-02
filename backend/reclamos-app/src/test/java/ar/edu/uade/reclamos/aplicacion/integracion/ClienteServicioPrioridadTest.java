package ar.edu.uade.reclamos.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** El cliente de REST 2: qué manda, cómo lee la respuesta y cuándo abre el circuito. */
class ClienteServicioPrioridadTest {

    private static final String URL = "http://prioridad.test/api/prioridades/evaluar";

    private MockRestServiceServer servidor;
    private ClienteServicioPrioridad cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder http = RestClient.builder().baseUrl("http://prioridad.test");
        servidor = MockRestServiceServer.bindTo(http).build();
        cliente = new ClienteServicioPrioridad(http.build(), ClienteServicioPrioridad.circuito(3, Duration.ofMinutes(1)));
    }

    @AfterEach
    void verificar() {
        servidor.verify();
    }

    @Test
    void mandaSoloCategoriaYDescripcionYLeeLaSugerencia() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.categoria").value("Luminaria rota"))
                .andExpect(jsonPath("$.descripcion").value("Cables expuestos"))
                .andExpect(jsonPath("$.ciudadano").doesNotExist())
                .andExpect(jsonPath("$.direccion").doesNotExist())
                .andRespond(withSuccess("""
                        {"prioridad": "CRITICA", "motivo": "  Riesgo eléctrico. ", "proveedor": "gemini",
                         "modelo": "gemini-3.5-flash-lite"}""", MediaType.APPLICATION_JSON));

        assertThat(cliente.sugerir("Luminaria rota", "Cables expuestos")).isEqualTo(
                new SugerenciaPrioridad(Prioridad.CRITICA, "Riesgo eléctrico.", "gemini", "gemini-3.5-flash-lite"));
    }

    @Test
    void errorDelServicioEsServicioNoDisponible() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> cliente.sugerir("Bache", "Pozo"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("HTTP 503");
    }

    @Test
    void prioridadDesconocidaOSinMotivoEsServicioNoDisponible() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"prioridad\": \"URGENTE\", \"motivo\": \"x\"}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"prioridad\": \"ALTA\", \"motivo\": \" \"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.sugerir("Bache", "Pozo"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("desconocida");
        assertThatThrownBy(() -> cliente.sugerir("Bache", "Pozo"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("incompleto");
    }

    @Test
    void motivoLargoSeRecortaParaQueEntreEnElReclamo() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"prioridad\": \"ALTA\", \"motivo\": \"" + "m".repeat(400) + "\"}", MediaType.APPLICATION_JSON));

        assertThat(cliente.sugerir("Bache", "Pozo").motivo()).hasSize(300).endsWith("…");
    }

    @Test
    void despuesDeVariasFallasElCircuitoSeAbreYNoVuelveALlamar() {
        servidor.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withServerError());

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cliente.sugerir("Bache", "Pozo"))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
        }
        assertThat(cliente.estadoCircuito()).isEqualTo(CircuitBreaker.State.OPEN);

        // La cuarta no llega al servidor: si llegara, MockRestServiceServer fallaría el test.
        assertThatThrownBy(() -> cliente.sugerir("Bache", "Pozo"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("circuito");
    }

    @Test
    void servicioQueNoContestaSeCortaEnElTiempoLimite() throws IOException {
        try (ServerSocket mudo = new ServerSocket(0)) {
            ClienteServicioPrioridad lento = ClienteServicioPrioridad.crear("http://localhost:" + mudo.getLocalPort(),
                    Duration.ofMillis(300), Duration.ofMillis(300), 5, Duration.ofSeconds(30));
            long inicio = System.nanoTime();

            assertThatThrownBy(() -> lento.sugerir("Bache", "Pozo"))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("no respondió");
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
        }
    }
}
