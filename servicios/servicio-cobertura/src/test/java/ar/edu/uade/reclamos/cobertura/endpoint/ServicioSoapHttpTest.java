package ar.edu.uade.reclamos.cobertura.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * El servicio levantado de verdad, por HTTP, como lo usa el backend: WSDL publicado y mensajes SOAP 1.1.
 * Además guarda el WSDL en {@code docs/contratos/soap/cobertura.wsdl} para tenerlo en el repositorio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServicioSoapHttpTest {

    private static final Path WSDL_EN_DOCS = Path.of("../../docs/contratos/soap/cobertura.wsdl");

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int puerto;

    @Test
    void publicaElWsdlConLaOperacionYLaDireccionDelServidor() throws Exception {
        HttpResponse<String> respuesta = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/ws/cobertura.wsdl")).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        String wsdl = respuesta.body();
        assertThat(wsdl).contains("targetNamespace=\"http://uade.edu.ar/reclamos/cobertura/v1\"")
                .contains("name=\"CoberturaPort\"")
                .contains("name=\"ConsultarCobertura\"")
                .contains("name=\"CoberturaService\"")
                .contains("location=\"" + base() + "/ws\"");

        guardarEnDocs(wsdl.replace(base() + "/ws", "http://localhost:8081/ws"));
    }

    @Test
    void respondeUnaConsultaValida() throws Exception {
        HttpResponse<String> respuesta = enviar("1", "2", "2");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("Obras Públicas").contains("Mantenimiento Vial")
                .doesNotContain("Fault");
    }

    @Test
    void respondeFaultDeClienteConHttp500() throws Exception {
        HttpResponse<String> respuesta = enviar("1", "2", "99");

        assertThat(respuesta.statusCode()).isEqualTo(500);
        assertThat(respuesta.body()).contains("Client").contains("No existe la categoría 99");
    }

    private HttpResponse<String> enviar(String municipio, String barrio, String categoria)
            throws IOException, InterruptedException {
        String sobre = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:cob="http://uade.edu.ar/reclamos/cobertura/v1">
                  <soapenv:Body>
                    <cob:ConsultarCoberturaRequest>
                      <cob:municipioId>%s</cob:municipioId>
                      <cob:barrioId>%s</cob:barrioId>
                      <cob:categoriaId>%s</cob:categoriaId>
                    </cob:ConsultarCoberturaRequest>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(municipio, barrio, categoria);
        return http.send(HttpRequest.newBuilder(URI.create(base() + "/ws"))
                        .header("Content-Type", "text/xml; charset=utf-8")
                        .header("SOAPAction", "\"\"")
                        .POST(HttpRequest.BodyPublishers.ofString(sobre, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String base() {
        return "http://localhost:" + puerto;
    }

    private static void guardarEnDocs(String wsdl) throws IOException {
        if (Files.isDirectory(WSDL_EN_DOCS.getParent())) {
            Files.writeString(WSDL_EN_DOCS, wsdl.endsWith("\n") ? wsdl : wsdl + "\n", StandardCharsets.UTF_8);
        }
    }
}
