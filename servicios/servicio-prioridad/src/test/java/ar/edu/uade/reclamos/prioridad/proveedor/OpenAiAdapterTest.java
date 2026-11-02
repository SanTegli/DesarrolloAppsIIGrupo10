package ar.edu.uade.reclamos.prioridad.proveedor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** El segundo Adapter: la misma consulta, otro formato de API. */
class OpenAiAdapterTest {

    private static final String URL = "https://openai.test/v1/chat/completions";
    private static final ConsultaIA CONSULTA = new ConsultaIA("instrucciones", "<descripcion>Bache</descripcion>");

    private MockRestServiceServer servidor;
    private OpenAiAdapter openai;

    @BeforeEach
    void preparar() {
        RestClient.Builder http = RestClient.builder().baseUrl("https://openai.test");
        servidor = MockRestServiceServer.bindTo(http).build();
        openai = new OpenAiAdapter(http.build(), "sk-prueba", "gpt-5.4-mini", new ObjectMapper());
    }

    @AfterEach
    void verificar() {
        servidor.verify();
    }

    @Test
    void mandaMensajesDeSistemaYUsuarioConEsquemaEstricto() {
        servidor.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer sk-prueba"))
                .andExpect(jsonPath("$.model").value("gpt-5.4-mini"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("<descripcion>Bache</descripcion>"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.additionalProperties").value(false))
                .andRespond(withSuccess("""
                        {"choices": [{"message": {"role": "assistant",
                          "content": "{\\"prioridad\\": \\"MEDIA\\", \\"motivo\\": \\"Bache chico.\\"}"}}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(openai.sugerir(CONSULTA)).isEqualTo(new RespuestaIA("MEDIA", "Bache chico."));
        assertThat(openai.nombre()).isEqualTo("openai");
    }

    @Test
    void siElModeloSeNiegaLaRespuestaEsInvalida() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": null, \"refusal\": \"No.\"}}]}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> openai.sugerir(CONSULTA)).isInstanceOf(RespuestaInvalidaException.class);
    }

    @Test
    void claveRechazadaEsProveedorNoDisponible() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> openai.sugerir(CONSULTA))
                .isInstanceOf(ProveedorNoDisponibleException.class).hasMessageContaining("clave")
                .hasMessageNotContaining("sk-prueba");
    }

    @Test
    void sinClaveNoLlama() {
        OpenAiAdapter sinClave = new OpenAiAdapter(RestClient.create(), null, "gpt-5.4-mini", new ObjectMapper());

        assertThatThrownBy(() -> sinClave.sugerir(CONSULTA))
                .isInstanceOf(ProveedorNoDisponibleException.class).hasMessageContaining("OPENAI_API_KEY");
    }
}
