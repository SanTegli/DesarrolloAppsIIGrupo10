package ar.edu.uade.reclamos.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.BarrioRepository;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MunicipioRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El alta de un reclamo con {@code reclamos.cobertura.origen: soap}, de punta a punta: REST, fachada,
 * {@code ServicioAsignacion}, {@link CoberturaSoap}, HTTP y JAXB reales. Del otro lado hay un servidor HTTP
 * mínimo que contesta sobres SOAP fijos, así el test no depende de levantar {@code servicio-cobertura}.
 *
 * <p>Cubre los cuatro casos del PR 9: caso válido, lista vacía, fault por id inválido y servicio lento.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:cobertura-soap;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.cobertura.origen=soap", "reclamos.cobertura.tiempo-limite=500ms"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AsignacionPorSoapIntegrationTest {

    private enum Respuesta { AREA, VACIA, FAULT, LENTA }

    private static final List<String> PEDIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer SOAP = iniciarServidorSoap();
    private static volatile Respuesta respuesta = Respuesta.AREA;
    private static volatile long areaQueCubre;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;

    private Long municipio;
    private Long barrio;
    private Long categoria;
    private Long area;
    private Long ciudadano;

    @DynamicPropertySource
    static void direccionDelServicio(DynamicPropertyRegistry propiedades) {
        propiedades.add("reclamos.cobertura.url", () -> "http://localhost:" + SOAP.getAddress().getPort() + "/ws");
    }

    @AfterAll
    static void apagarServidorSoap() {
        SOAP.stop(0);
    }

    @BeforeEach
    void escenario() {
        PEDIDOS.clear();
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            for (String tabla : List.of("notificaciones", "historial_estados", "reclamos", "area_barrio",
                    "area_categoria", "usuarios", "areas_municipales", "barrios", "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Barrio bernal = barrios.guardar(new Barrio("Bernal", quilmes));
            Categoria bache = categorias.guardar(new Categoria("Bache", "Pozo en la calzada", 120, Prioridad.BAJA));
            // Sin barrios ni categorías locales: con origen soap, la cobertura la decide el servicio.
            AreaMunicipal obras = areas.guardar(new AreaMunicipal("Obras Públicas", "obras@example.org", quilmes));
            Ciudadano ana = (Ciudadano) usuarios.guardar(
                    new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null));
            municipio = quilmes.getId();
            barrio = bernal.getId();
            categoria = bache.getId();
            area = obras.getId();
            ciudadano = ana.getId();
        });
        areaQueCubre = area;
    }

    @Test
    void casoValidoAsignaElAreaQueDevuelveElServicio() throws Exception {
        respuesta = Respuesta.AREA;

        crearReclamo().andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ASIGNADO"))
                .andExpect(jsonPath("$.area.id").value(area))
                .andExpect(jsonPath("$.asignacionPendiente").value(false));
        assertThat(PEDIDOS).singleElement().asString()
                .contains("http://uade.edu.ar/reclamos/cobertura/v1")
                .containsSubsequence("municipioId>" + municipio + "<", "barrioId>" + barrio + "<",
                        "categoriaId>" + categoria + "<");
    }

    @Test
    void listaVaciaDejaElReclamoIngresadoSinMarcarlo() throws Exception {
        respuesta = Respuesta.VACIA;

        crearReclamo().andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("INGRESADO"))
                .andExpect(jsonPath("$.area").value(nullValue()))
                .andExpect(jsonPath("$.asignacionPendiente").value(false));
    }

    @Test
    void faultDelServicioDejaLaAsignacionPendiente() throws Exception {
        respuesta = Respuesta.FAULT;

        crearReclamo().andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("INGRESADO"))
                .andExpect(jsonPath("$.asignacionPendiente").value(true));
    }

    @Test
    void servicioLentoDejaLaAsignacionPendienteSinTrabarElAlta() throws Exception {
        respuesta = Respuesta.LENTA;
        long inicio = System.nanoTime();

        crearReclamo().andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("INGRESADO"))
                .andExpect(jsonPath("$.asignacionPendiente").value(true));
        // El servidor tarda 3 segundos; el alta tiene que cortar a los 500 ms del tiempo límite.
        assertThat((System.nanoTime() - inicio) / 1_000_000).as("milisegundos del alta").isLessThan(2_500);
    }

    private ResultActions crearReclamo() throws Exception {
        return mvc.perform(post("/api/reclamos").header("X-Usuario-Id", ciudadano)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoriaId": %d, "barrioId": %d, "descripcion": "Pozo en la calle",
                         "direccion": "Belgrano 450"}""".formatted(categoria, barrio)));
    }

    private static HttpServer iniciarServidorSoap() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool(tarea -> {
                Thread hilo = new Thread(tarea, "soap-falso");
                hilo.setDaemon(true);
                return hilo;
            }));
            servidor.createContext("/ws", AsignacionPorSoapIntegrationTest::responder);
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void responder(HttpExchange intercambio) throws IOException {
        PEDIDOS.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        int estado = 200;
        String cuerpo = switch (respuesta) {
            case AREA -> "<cob:ConsultarCoberturaResponse xmlns:cob=\"http://uade.edu.ar/reclamos/cobertura/v1\">"
                    + "<cob:area><cob:id>" + areaQueCubre + "</cob:id><cob:nombre>Obras Públicas</cob:nombre></cob:area>"
                    + "</cob:ConsultarCoberturaResponse>";
            case VACIA -> "<cob:ConsultarCoberturaResponse xmlns:cob=\"http://uade.edu.ar/reclamos/cobertura/v1\"/>";
            case FAULT -> {
                estado = 500;
                yield "<SOAP-ENV:Fault><faultcode>SOAP-ENV:Client</faultcode>"
                        + "<faultstring xml:lang=\"es\">No existe el barrio 9</faultstring></SOAP-ENV:Fault>";
            }
            case LENTA -> {
                dormir(3_000);
                yield "<cob:ConsultarCoberturaResponse xmlns:cob=\"http://uade.edu.ar/reclamos/cobertura/v1\"/>";
            }
        };
        byte[] sobre = ("<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<SOAP-ENV:Body>" + cuerpo + "</SOAP-ENV:Body></SOAP-ENV:Envelope>")
                .getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
        try {
            intercambio.sendResponseHeaders(estado, sobre.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(sobre);
            }
        } catch (IOException clienteSeFue) {
            // En el caso LENTA el backend ya cortó la conexión por tiempo límite.
        }
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
