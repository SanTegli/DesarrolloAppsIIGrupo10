package ar.edu.uade.reclamos.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.anything;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withClientOrSenderFault;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Entidad;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

/**
 * El Adapter {@link CoberturaSoap}: qué manda al servicio SOAP, cómo traduce la respuesta a áreas del
 * dominio y qué hace con cada falla. {@link MockWebServiceServer} reemplaza la red y responde lo que cada
 * caso necesita; los dos últimos tests usan sockets reales para probar el tiempo límite.
 */
class CoberturaSoapTest {

    private static final String NS = "http://uade.edu.ar/reclamos/cobertura/v1";

    private final Municipio quilmes = conId(new Municipio("Quilmes", "Buenos Aires"), 1L);
    private final Municipio otroMunicipio = conId(new Municipio("Berazategui", "Buenos Aires"), 2L);
    private final AreaMunicipal obras = area(2L, "Obras Públicas", quilmes);
    private final AreaMunicipal vial = area(4L, "Mantenimiento Vial", quilmes);
    private final AreaMunicipal inactiva = area(5L, "Bacheo nocturno", quilmes);
    private final AreaMunicipal ajena = area(6L, "Obras de Berazategui", otroMunicipio);
    private final DoblesEnMemoria.Areas areas = new DoblesEnMemoria.Areas(obras, vial, inactiva, ajena);

    private WebServiceTemplate cliente;
    private MockWebServiceServer servidor;
    private CoberturaSoap cobertura;

    @BeforeEach
    void preparar() {
        inactiva.desactivar();
        cliente = CoberturaSoap.crearCliente("http://cobertura.test/ws", Duration.ofSeconds(2));
        servidor = MockWebServiceServer.createServer(cliente);
        cobertura = new CoberturaSoap(cliente, areas);
    }

    @AfterEach
    void verificar() {
        servidor.verify();
    }

    @Test
    void mandaLosTresIdsYDevuelveLasAreasDelBackendOrdenadasPorId() {
        servidor.expect(payload(new StringSource("<ConsultarCoberturaRequest xmlns=\"" + NS + "\">"
                        + "<municipioId>1</municipioId><barrioId>2</barrioId><categoriaId>3</categoriaId>"
                        + "</ConsultarCoberturaRequest>")))
                .andRespond(withPayload(respuesta(4, 2)));

        assertThat(cobertura.buscarCandidatas(1L, 2L, 3L)).containsExactly(obras, vial);
    }

    @Test
    void listaVaciaNoEsUnaFalla() {
        servidor.expect(payload(new StringSource("<ConsultarCoberturaRequest xmlns=\"" + NS + "\">"
                        + "<municipioId>1</municipioId><barrioId>3</barrioId><categoriaId>1</categoriaId>"
                        + "</ConsultarCoberturaRequest>")))
                .andRespond(withPayload(respuesta()));

        assertThat(cobertura.buscarCandidatas(1L, 3L, 1L)).isEmpty();
    }

    @Test
    void descartaAreasQueElBackendNoTieneInactivasODeOtroMunicipio() {
        servidor.expect(anything())
                .andRespond(withPayload(respuesta(99, 5, 6, 2, 2)));

        assertThat(cobertura.buscarCandidatas(1L, 2L, 2L)).containsExactly(obras);
    }

    @Test
    void faultDelServicioDejaLaAsignacionPendienteConElMotivo() {
        servidor.expect(anything())
                .andRespond(withClientOrSenderFault("No existe el barrio 9", Locale.of("es")));

        assertThatThrownBy(() -> cobertura.buscarCandidatas(1L, 9L, 2L))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasMessageContaining("rechazó la consulta").hasMessageContaining("No existe el barrio 9");
    }

    @Test
    void errorDeRedEsServicioNoDisponible() {
        servidor.expect(anything())
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cobertura.buscarCandidatas(1L, 2L, 2L))
                .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("no respondió");
    }

    @Test
    void respuestaQueNoEsDelContratoEsServicioNoDisponible() {
        servidor.expect(anything())
                .andRespond(withPayload(new StringSource("<Otra xmlns=\"urn:otra\"/>")));

        assertThatThrownBy(() -> cobertura.buscarCandidatas(1L, 2L, 2L))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    void servicioQueNoContestaSeCortaEnElTiempoLimite() throws IOException {
        // Acepta la conexión (queda en la cola del socket) pero nunca responde.
        try (ServerSocket mudo = new ServerSocket(0)) {
            CoberturaSoap conTiempoCorto = new CoberturaSoap(CoberturaSoap.crearCliente(
                    "http://localhost:" + mudo.getLocalPort() + "/ws", Duration.ofMillis(300)), areas);
            long inicio = System.nanoTime();

            assertThatThrownBy(() -> conTiempoCorto.buscarCandidatas(1L, 2L, 2L))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("no respondió");
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
        }
    }

    @Test
    void servicioApagadoEsServicioNoDisponible() throws IOException {
        int puertoLibre;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoLibre = socket.getLocalPort();
        }
        CoberturaSoap apagado = new CoberturaSoap(CoberturaSoap.crearCliente(
                "http://localhost:" + puertoLibre + "/ws", Duration.ofMillis(500)), areas);

        assertThatThrownBy(() -> apagado.buscarCandidatas(1L, 2L, 2L))
                .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("no respondió");
    }

    private static StringSource respuesta(long... ids) {
        StringBuilder xml = new StringBuilder("<ConsultarCoberturaResponse xmlns=\"" + NS + "\">");
        for (long id : ids) {
            xml.append("<area><id>").append(id).append("</id><nombre>Área ").append(id).append("</nombre></area>");
        }
        return new StringSource(xml.append("</ConsultarCoberturaResponse>").toString());
    }

    private static AreaMunicipal area(Long id, String nombre, Municipio municipio) {
        return conId(new AreaMunicipal(nombre, "area" + id + "@example.org", municipio), id);
    }

    private static <T extends Entidad> T conId(T entidad, Long id) {
        entidad.setId(id);
        return entidad;
    }
}
