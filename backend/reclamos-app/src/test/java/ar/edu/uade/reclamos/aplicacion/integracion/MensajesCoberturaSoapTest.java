package ar.edu.uade.reclamos.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import ar.edu.uade.reclamos.aplicacion.integracion.soap.AreaCubierta;
import ar.edu.uade.reclamos.aplicacion.integracion.soap.ConsultarCoberturaRequest;
import ar.edu.uade.reclamos.aplicacion.integracion.soap.ConsultarCoberturaResponse;
import java.nio.file.Path;
import java.time.Duration;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;
import org.xml.sax.SAXException;

/**
 * Las clases JAXB del backend están escritas a mano: este test comprueba contra el XSD publicado
 * ({@code docs/contratos/soap/cobertura.xsd}) que el pedido que arman es válido y que entienden una
 * respuesta válida. Si el contrato cambia y el cliente no, falla acá y no en la demo.
 */
class MensajesCoberturaSoapTest {

    private static final Path XSD = Path.of("../../docs/contratos/soap/cobertura.xsd");

    private static Schema contrato;
    private static Marshaller marshaller;
    private static Unmarshaller unmarshaller;

    @BeforeAll
    static void cargar() throws SAXException {
        contrato = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(XSD.toFile());
        WebServiceTemplate cliente = CoberturaSoap.crearCliente("http://cobertura.test/ws", Duration.ofSeconds(1));
        marshaller = cliente.getMarshaller();
        unmarshaller = cliente.getUnmarshaller();
    }

    @Test
    void elPedidoQueArmaElBackendCumpleElXsd() throws Exception {
        StringResult xml = new StringResult();
        marshaller.marshal(new ConsultarCoberturaRequest(1, 2, 3), xml);

        contrato.newValidator().validate(new StringSource(xml.toString()));
        assertThat(xml.toString()).contains("http://uade.edu.ar/reclamos/cobertura/v1")
                .containsSubsequence("municipioId>1<", "barrioId>2<", "categoriaId>3<");
    }

    @Test
    void entiendeUnaRespuestaValidaDelContrato() throws Exception {
        String respuesta = """
                <cob:ConsultarCoberturaResponse xmlns:cob="http://uade.edu.ar/reclamos/cobertura/v1">
                  <cob:area><cob:id>2</cob:id><cob:nombre>Obras Públicas</cob:nombre></cob:area>
                  <cob:area><cob:id>4</cob:id><cob:nombre>Mantenimiento Vial</cob:nombre></cob:area>
                </cob:ConsultarCoberturaResponse>""";
        contrato.newValidator().validate(new StringSource(respuesta));

        Object leida = unmarshaller.unmarshal(new StringSource(respuesta));

        assertThat(leida).isInstanceOf(ConsultarCoberturaResponse.class);
        assertThat(((ConsultarCoberturaResponse) leida).getAreas())
                .extracting(AreaCubierta::getId, AreaCubierta::getNombre)
                .containsExactly(tuple(2L, "Obras Públicas"), tuple(4L, "Mantenimiento Vial"));
    }

    @Test
    void entiendeLaRespuestaVacia() throws Exception {
        String vacia = "<ConsultarCoberturaResponse xmlns=\"http://uade.edu.ar/reclamos/cobertura/v1\"/>";
        contrato.newValidator().validate(new StringSource(vacia));

        ConsultarCoberturaResponse leida = (ConsultarCoberturaResponse) unmarshaller.unmarshal(new StringSource(vacia));

        assertThat(leida.getAreas()).isEmpty();
    }

    @Test
    void elXsdRechazaIdsQueNoSonPositivos() {
        assertThatThrownBy(() -> contrato.newValidator().validate(new StringSource(
                "<ConsultarCoberturaRequest xmlns=\"http://uade.edu.ar/reclamos/cobertura/v1\">"
                        + "<municipioId>0</municipioId><barrioId>2</barrioId><categoriaId>3</categoriaId>"
                        + "</ConsultarCoberturaRequest>")))
                .isInstanceOf(SAXException.class);
    }
}
