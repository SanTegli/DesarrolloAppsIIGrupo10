package ar.edu.uade.reclamos.cobertura.endpoint;

import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.payload;
import static org.springframework.ws.test.server.ResponseMatchers.validPayload;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import ar.edu.uade.reclamos.cobertura.configuracion.ConfiguracionWebService;
import java.io.IOException;
import java.util.Map;
import javax.xml.transform.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

/**
 * La operación {@code ConsultarCobertura} completa del lado del servidor (validación contra el XSD, endpoint,
 * catálogo y faults), sin levantar HTTP.
 */
@SpringBootTest
class CoberturaEndpointTest {

    private static final Map<String, String> NS = Map.of("c", ConfiguracionWebService.NAMESPACE);

    @Autowired
    private ApplicationContext contexto;

    private MockWebServiceClient cliente;

    @BeforeEach
    void crearCliente() {
        cliente = MockWebServiceClient.createClient(contexto);
    }

    @Test
    void casoValidoDevuelveLasAreasQueCubren() throws IOException {
        cliente.sendRequest(withPayload(pedido("1", "2", "2")))
                .andExpect(noFault())
                .andExpect(validPayload(new ClassPathResource("cobertura.xsd")))
                .andExpect(payload(new StringSource(
                        "<ConsultarCoberturaResponse xmlns=\"http://uade.edu.ar/reclamos/cobertura/v1\">"
                        + "<area><id>2</id><nombre>Obras Públicas</nombre></area>"
                        + "<area><id>4</id><nombre>Mantenimiento Vial</nombre></area>"
                        + "</ConsultarCoberturaResponse>")));
    }

    @Test
    void combinacionSinAreasDevuelveLaRespuestaVacia() {
        cliente.sendRequest(withPayload(pedido("1", "3", "1")))
                .andExpect(noFault())
                .andExpect(xpath("count(/c:ConsultarCoberturaResponse/c:area)", NS).evaluatesTo(0));
    }

    @Test
    void idDesconocidoDevuelveFaultDeCliente() {
        cliente.sendRequest(withPayload(pedido("1", "99", "2")))
                .andExpect(clientOrSenderFault("No existe el barrio 99"));
    }

    @Test
    void idQueNoCumpleElXsdSeRechazaAntesDeConsultar() {
        cliente.sendRequest(withPayload(pedido("0", "2", "2")))
                .andExpect(clientOrSenderFault("El pedido no cumple el contrato cobertura.xsd"));
        cliente.sendRequest(withPayload(pedido("uno", "2", "2")))
                .andExpect(clientOrSenderFault());
    }

    @Test
    void pedidoIncompletoSeRechaza() {
        cliente.sendRequest(withPayload(new StringSource("""
                        <ConsultarCoberturaRequest xmlns="http://uade.edu.ar/reclamos/cobertura/v1">
                          <municipioId>1</municipioId>
                          <barrioId>2</barrioId>
                        </ConsultarCoberturaRequest>""")))
                .andExpect(clientOrSenderFault());
    }

    private static Source pedido(String municipio, String barrio, String categoria) {
        return new StringSource("""
                <ConsultarCoberturaRequest xmlns="http://uade.edu.ar/reclamos/cobertura/v1">
                  <municipioId>%s</municipioId>
                  <barrioId>%s</barrioId>
                  <categoriaId>%s</categoriaId>
                </ConsultarCoberturaRequest>""".formatted(municipio, barrio, categoria));
    }
}
