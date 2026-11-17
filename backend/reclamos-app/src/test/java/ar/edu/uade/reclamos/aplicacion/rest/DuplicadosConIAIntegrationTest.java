package ar.edu.uade.reclamos.aplicacion.rest;

import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Fotos.alta;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * PR 27: los posibles duplicados del alta comparados por la IA, con un REST 2 falso. Ana reclamó un farol como
 * "Otro problema" y Bruno, a 20 metros, reclama una luminaria: la IA dice que es el mismo problema aunque la
 * categoría sea otra. Un bache en la misma esquina también está cerca, pero no es el mismo.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:duplicadosia;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.uso.reclamos-por-dia=20", "reclamos.prioridad.estrategia=ia",
        "reclamos.prioridad.ia.tiempo-respuesta=1s", "reclamos.prioridad.ia.circuito.llamadas=1000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DuplicadosConIAIntegrationTest {

    private enum Respuesta { EL_DE_ANA, NINGUNO, INVENTADO, ERROR }

    private static final double LATITUD = -34.7361;
    private static final double LONGITUD = -58.3229;
    private static final double LATITUD_VECINA = LATITUD + 0.00018;

    private static final HttpServer REST2 = iniciarServidor();
    private static final List<String> PEDIDOS = new CopyOnWriteArrayList<>();
    private static volatile Respuesta respuesta = Respuesta.ERROR;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired CategoriaRepository categorias;
    @Autowired UsuarioRepository usuarios;

    private Long ana;
    private Long bruno;
    private Long municipio;
    private Long luminaria;
    private Long otro;
    private Long bache;

    @DynamicPropertySource
    static void direccionDelServicio(DynamicPropertyRegistry propiedades) {
        propiedades.add("reclamos.prioridad.ia.url", () -> "http://localhost:" + REST2.getAddress().getPort());
    }

    @AfterAll
    static void apagarServidor() {
        REST2.stop(0);
    }

    @BeforeEach
    void escenario() {
        respuesta = Respuesta.ERROR;
        PEDIDOS.clear();
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            for (String tabla : List.of("apoyos", "codigos_verificacion", "notificaciones", "historial_estados",
                    "reclamos", "area_categoria", "usuarios", "areas_municipales", "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            municipio = municipios.guardar(new Municipio("Quilmes", "Buenos Aires")).getId();
            luminaria = categorias.guardar(new Categoria("Luminaria rota", "Farol", 48, Prioridad.MEDIA)).getId();
            otro = categorias.guardar(new Categoria("Otro problema", "Ninguna", 72, Prioridad.MEDIA, true)).getId();
            bache = categorias.guardar(new Categoria("Bache", "Pozo", 120, Prioridad.BAJA)).getId();
            ana = usuarios.guardar(verificado(new Ciudadano("30111222", "Ana", "Perez", "ana@example.org", null)))
                    .getId();
            bruno = usuarios.guardar(verificado(new Ciudadano("31222333", "Bruno", "Diaz", "bruno@example.org",
                    null))).getId();
        });
    }

    @Test
    void laIaMarcaElMismoProblemaAunqueSeaDeOtraCategoriaYLoPoneAdelante() throws Exception {
        String deAna = crear(ana, otro, "No hay luz en la esquina, el poste está apagado", "\"categoriaPropuesta\": \"Sin luz\", ");
        crear(ana, bache, "Pozo enorme en la esquina", "");
        respuesta = Respuesta.EL_DE_ANA;

        duplicados(bruno, luminaria, "El farol de la esquina no prende")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparadoPorIA").value(true))
                .andExpect(jsonPath("$.motivo").value("Los dos hablan del mismo poste apagado."))
                .andExpect(jsonPath("$.candidatos", hasSize(1)))
                .andExpect(jsonPath("$.candidatos[0].numero").value(deAna))
                .andExpect(jsonPath("$.candidatos[0].masParecido").value(true))
                .andExpect(jsonPath("$.candidatos[0].categoria.nombre").value("Otro problema"));
        // A REST 2 le llegan los dos cercanos, de cualquier categoría, solo con categoría y descripción.
        JsonNode pedido = json.readTree(PEDIDOS.get(PEDIDOS.size() - 1));
        assertThat(pedido.get("nuevo").get("categoria").asText()).isEqualTo("Luminaria rota");
        assertThat(pedido.get("candidatos")).hasSize(2);
        assertThat(PEDIDOS.get(PEDIDOS.size() - 1)).doesNotContain("Ana", "Perez", "Belgrano", "REC-", "foto");
    }

    @Test
    void siLaIaDiceQueNingunoQuedanLosDeLaMismaCategoria() throws Exception {
        String farol = crear(ana, luminaria, "Farol apagado", "");
        respuesta = Respuesta.NINGUNO;

        duplicados(bruno, luminaria, "Cables sueltos en el poste")
                .andExpect(jsonPath("$.comparadoPorIA").value(true))
                .andExpect(jsonPath("$.motivo").value("Son problemas distintos."))
                .andExpect(jsonPath("$.candidatos[0].numero").value(farol))
                .andExpect(jsonPath("$.candidatos[0].masParecido").value(false));
    }

    @Test
    void siLaIaFallaOEligeUnoQueNoMandamosElAltaSigueSinComparacion() throws Exception {
        crear(ana, luminaria, "Farol apagado", "");
        String otroDeAna = crear(ana, bache, "Pozo", "");

        respuesta = Respuesta.ERROR;
        duplicados(bruno, bache, "Pozo").andExpect(jsonPath("$.comparadoPorIA").value(false))
                .andExpect(jsonPath("$.candidatos", hasSize(1)))
                .andExpect(jsonPath("$.candidatos[0].numero").value(otroDeAna));
        respuesta = Respuesta.INVENTADO;
        duplicados(bruno, bache, "Pozo").andExpect(jsonPath("$.comparadoPorIA").value(false));
    }

    @Test
    void sinReclamosCercanosNoLlamaALaIa() throws Exception {
        respuesta = Respuesta.EL_DE_ANA;

        duplicados(bruno, luminaria, "Farol apagado").andExpect(jsonPath("$.comparadoPorIA").value(false))
                .andExpect(jsonPath("$.candidatos", hasSize(0)));
        assertThat(PEDIDOS).isEmpty();
    }

    private String crear(Long usuario, Long categoria, String descripcion, String extra) throws Exception {
        String cuerpo = """
                {"categoriaId": %d, "municipioId": %d, "descripcion": "%s", "direccion": "Belgrano 450", %s
                 "ubicacion": {"latitud": %s, "longitud": %s, "direccionNormalizada": "GRL BELGRANO 450, Bernal"}}"""
                .formatted(categoria, municipio, descripcion, extra, LATITUD, LONGITUD);
        String respuestaAlta = mvc.perform(alta(cuerpo).with(como(usuario))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(respuestaAlta).get("numero").asText();
    }

    private ResultActions duplicados(Long usuario, Long categoria, String descripcion) throws Exception {
        return mvc.perform(post("/api/reclamos/candidatos-duplicados").with(como(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\": %d, \"descripcion\": \"%s\", \"latitud\": %s, \"longitud\": %s}"
                        .formatted(categoria, descripcion, LATITUD_VECINA, LONGITUD)));
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool(tarea -> {
                Thread hilo = new Thread(tarea, "rest2-falso-duplicados");
                hilo.setDaemon(true);
                return hilo;
            }));
            servidor.createContext("/api/prioridades/evaluar", intercambio -> responder(intercambio, 504,
                    "{\"status\": 504, \"codigo\": \"TIEMPO_AGOTADO\", \"mensaje\": \"Sin respuesta\"}"));
            servidor.createContext("/api/duplicados/comparar", DuplicadosConIAIntegrationTest::comparar);
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** "El de Ana" es el candidato cuya descripción habla del poste: el falso lee el pedido como la IA. */
    private static void comparar(HttpExchange intercambio) throws IOException {
        String pedido = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        PEDIDOS.add(pedido);
        String datos = ", \"proveedor\": \"simulado\", \"modelo\": \"reglas\"}";
        switch (respuesta) {
            case EL_DE_ANA -> {
                JsonNode candidatos = new ObjectMapper().readTree(pedido).get("candidatos");
                String id = "1";
                for (JsonNode candidato : candidatos) {
                    if (candidato.get("descripcion").asText().contains("poste")) {
                        id = candidato.get("id").asText();
                    }
                }
                responder(intercambio, 200, "{\"similar\": \"" + id
                        + "\", \"motivo\": \"Los dos hablan del mismo poste apagado.\"" + datos);
            }
            case NINGUNO -> responder(intercambio, 200,
                    "{\"similar\": null, \"motivo\": \"Son problemas distintos.\"" + datos);
            case INVENTADO -> responder(intercambio, 200, "{\"similar\": \"99\", \"motivo\": \"x\"" + datos);
            case ERROR -> responder(intercambio, 504,
                    "{\"status\": 504, \"codigo\": \"TIEMPO_AGOTADO\", \"mensaje\": \"Sin respuesta\"}");
        }
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        intercambio.getRequestBody().readAllBytes();
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
