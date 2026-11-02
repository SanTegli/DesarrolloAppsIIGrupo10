package ar.edu.uade.reclamos.prioridad.proveedor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** El Adapter de Gemini contra una API simulada: los tests nunca llaman a Google ni necesitan clave. */
class GeminiAdapterTest {

    private static final String URL =
            "https://gemini.test/v1beta/models/gemini-3.5-flash-lite:generateContent";
    private static final ConsultaIA CONSULTA = new ConsultaIA("instrucciones de prueba",
            "<categoria>Luminaria rota</categoria>\n<descripcion>\nCables expuestos\n</descripcion>");

    private MockRestServiceServer servidor;
    private GeminiAdapter gemini;

    @BeforeEach
    void preparar() {
        RestClient.Builder http = RestClient.builder().baseUrl("https://gemini.test");
        servidor = MockRestServiceServer.bindTo(http).build();
        gemini = new GeminiAdapter(http.build(), "clave-de-prueba", "gemini-3.5-flash-lite", new ObjectMapper());
    }

    @AfterEach
    void verificar() {
        servidor.verify();
    }

    @Test
    void traduceLaConsultaAlFormatoDeGeminiYLeeLaRespuesta() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "clave-de-prueba"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("instrucciones de prueba"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("<descripcion>")))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.prioridad.enum[3]")
                        .value("CRITICA"))
                .andRespond(withSuccess(respuesta(
                        "{\\\"prioridad\\\": \\\"CRITICA\\\", \\\"motivo\\\": \\\"Riesgo eléctrico.\\\"}"),
                        MediaType.APPLICATION_JSON));

        assertThat(gemini.sugerir(CONSULTA)).isEqualTo(new RespuestaIA("CRITICA", "Riesgo eléctrico."));
        assertThat(gemini.nombre()).isEqualTo("gemini");
        assertThat(gemini.modelo()).isEqualTo("gemini-3.5-flash-lite");
    }

    @Test
    void ignoraLasPartesDeRazonamientoYLosBloquesDeCodigo() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("""
                {"candidates": [{"content": {"parts": [
                  {"text": "pensando...", "thought": true},
                  {"text": "```json\\n{\\"prioridad\\": \\"ALTA\\", \\"motivo\\": \\"Avenida.\\"}\\n```"}
                ]}, "finishReason": "STOP"}]}""", MediaType.APPLICATION_JSON));

        assertThat(gemini.sugerir(CONSULTA)).isEqualTo(new RespuestaIA("ALTA", "Avenida."));
    }

    @Test
    void sinClaveNoLlamaYQuedaNoDisponible() {
        GeminiAdapter sinClave = new GeminiAdapter(RestClient.create(), " ", "gemini-3.5-flash-lite",
                new ObjectMapper());

        assertThatThrownBy(() -> sinClave.sugerir(CONSULTA))
                .isInstanceOf(ProveedorNoDisponibleException.class).hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void cuotaAgotadaEsProveedorNoDisponible() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> gemini.sugerir(CONSULTA))
                .isInstanceOf(ProveedorNoDisponibleException.class).hasMessageContaining("cuota")
                .hasMessageNotContaining("clave-de-prueba");
    }

    @Test
    void claveOModeloRechazadosEsProveedorNoDisponible() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> gemini.sugerir(CONSULTA))
                .isInstanceOf(ProveedorNoDisponibleException.class).hasMessageContaining("HTTP 400");
    }

    @Test
    void errorDelServidorEsProveedorNoDisponible() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> gemini.sugerir(CONSULTA)).isInstanceOf(ProveedorNoDisponibleException.class);
    }

    @Test
    void respuestaBloqueadaSinTextoEsInvalida() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"candidates\": [{\"finishReason\": \"SAFETY\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gemini.sugerir(CONSULTA))
                .isInstanceOf(RespuestaInvalidaException.class).hasMessageContaining("SAFETY");
    }

    @Test
    void textoQueNoEsJsonEsRespuestaInvalida() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(respuesta("Diría que es ALTA"),
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gemini.sugerir(CONSULTA)).isInstanceOf(RespuestaInvalidaException.class);
    }

    @Test
    void proveedorQueNoContestaSeCortaEnElTiempoLimite() throws IOException {
        try (ServerSocket mudo = new ServerSocket(0)) {
            SimpleClientHttpRequestFactory conexion = new SimpleClientHttpRequestFactory();
            conexion.setConnectTimeout(300);
            conexion.setReadTimeout(300);
            GeminiAdapter lento = new GeminiAdapter(RestClient.builder()
                    .baseUrl("http://localhost:" + mudo.getLocalPort()).requestFactory(conexion).build(),
                    "clave-de-prueba", "gemini-3.5-flash-lite", new ObjectMapper());

            assertThatThrownBy(() -> lento.sugerir(CONSULTA)).isInstanceOf(TiempoAgotadoException.class);
        }
    }

    /** Respuesta de generateContent con el texto dado como única parte; el texto ya viene escapado. */
    private static String respuesta(String texto) {
        return "{\"candidates\": [{\"content\": {\"role\": \"model\", \"parts\": [{\"text\": \"" + texto
                + "\"}]}, \"finishReason\": \"STOP\"}], \"modelVersion\": \"gemini-3.5-flash-lite\"}";
    }
}
