package ar.edu.uade.reclamos.aplicacion.integracion;

import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
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
 * El alta de un reclamo con {@code reclamos.prioridad.estrategia: ia}, de punta a punta: REST, fachada,
 * {@code ReclamoService}, el Decorator con respaldo, {@code PrioridadPorIA} y el cliente HTTP reales. Del
 * otro lado hay un servidor mínimo que contesta como REST 2, así el test no depende de Gemini.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:prioridad-ia;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.prioridad.estrategia=ia", "reclamos.prioridad.ia.tiempo-respuesta=500ms",
        "reclamos.prioridad.ia.circuito.llamadas=1000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrioridadPorIAIntegrationTest {

    private enum Respuesta { CRITICA, ERROR, LENTA }

    private static final List<String> PEDIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer REST2 = iniciarServidor();
    private static volatile Respuesta respuesta = Respuesta.CRITICA;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;

    private Long barrio;
    private Long categoria;
    private Long ciudadano;

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
        PEDIDOS.clear();
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            for (String tabla : List.of("notificaciones", "historial_estados", "reclamos", "area_barrio",
                    "area_categoria", "usuarios", "areas_municipales", "barrios", "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Barrio bernal = barrios.guardar(new Barrio("Bernal", quilmes));
            Categoria luminaria = categorias.guardar(
                    new Categoria("Luminaria rota", "Farol apagado o dañado", 48, Prioridad.MEDIA));
            AreaMunicipal alumbrado = new AreaMunicipal("Alumbrado", "alumbrado@example.org", quilmes);
            alumbrado.agregarBarrio(bernal);
            alumbrado.agregarCategoria(luminaria);
            areas.guardar(alumbrado);
            Ciudadano ana = (Ciudadano) usuarios.guardar(
                    verificado(new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", "1155550000")));
            barrio = bernal.getId();
            categoria = luminaria.getId();
            ciudadano = ana.getId();
        });
    }

    @Test
    void laIaSubeLaPrioridadYQuedaRegistradoQuienLaDecidio() throws Exception {
        respuesta = Respuesta.CRITICA;

        crearReclamo().andExpect(status().isCreated())
                .andExpect(jsonPath("$.prioridad").value("CRITICA"))
                .andExpect(jsonPath("$.origenPrioridad").value("IA"))
                .andExpect(jsonPath("$.motivoPrioridad").value("Cables expuestos: riesgo eléctrico."))
                .andExpect(jsonPath("$.estado").value("ASIGNADO"));
    }

    @Test
    void alServicioDeIaSoloLeLlegaLaCategoriaYLaDescripcion() throws Exception {
        respuesta = Respuesta.CRITICA;

        crearReclamo().andExpect(status().isCreated());

        assertThat(PEDIDOS).singleElement().asString()
                .contains("Luminaria rota", "Poste con cables expuestos")
                .doesNotContain("Ana", "Pérez", "30111222", "ana@example.org", "1155550000", "Belgrano 450");
    }

    @Test
    void siLaIaFallaElReclamoSeCreaConLaPrioridadDeLaCategoria() throws Exception {
        respuesta = Respuesta.ERROR;

        crearReclamo().andExpect(status().isCreated())
                .andExpect(jsonPath("$.prioridad").value("MEDIA"))
                .andExpect(jsonPath("$.origenPrioridad").value("RESPALDO"))
                .andExpect(jsonPath("$.estado").value("ASIGNADO"));
    }

    @Test
    void siLaIaTardaElAltaNoEsperaMasQueElTiempoLimite() throws Exception {
        respuesta = Respuesta.LENTA;
        long inicio = System.nanoTime();

        crearReclamo().andExpect(status().isCreated()).andExpect(jsonPath("$.origenPrioridad").value("RESPALDO"));
        // El servidor tarda 3 segundos; el cliente corta a los 500 ms.
        assertThat((System.nanoTime() - inicio) / 1_000_000).as("milisegundos del alta").isLessThan(2_500);
    }

    private ResultActions crearReclamo() throws Exception {
        return mvc.perform(post("/api/reclamos").with(como(ciudadano))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoriaId": %d, "barrioId": %d, "descripcion": "Poste con cables expuestos",
                         "direccion": "Belgrano 450"}""".formatted(categoria, barrio)));
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool(tarea -> {
                Thread hilo = new Thread(tarea, "rest2-falso");
                hilo.setDaemon(true);
                return hilo;
            }));
            servidor.createContext("/api/prioridades/evaluar", PrioridadPorIAIntegrationTest::responder);
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
            case CRITICA -> "{\"prioridad\": \"CRITICA\", \"motivo\": \"Cables expuestos: riesgo eléctrico.\", "
                    + "\"proveedor\": \"gemini\", \"modelo\": \"gemini-3.5-flash-lite\"}";
            case ERROR -> {
                estado = 503;
                yield "{\"status\": 503, \"codigo\": \"PROVEEDOR_NO_DISPONIBLE\", \"mensaje\": \"Sin cuota\"}";
            }
            case LENTA -> {
                dormir(3_000);
                yield "{\"prioridad\": \"ALTA\", \"motivo\": \"Tarde.\", \"proveedor\": \"gemini\", \"modelo\": \"x\"}";
            }
        };
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        try {
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        } catch (IOException clienteSeFue) {
            // En el caso LENTA el backend ya cortó por tiempo límite.
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
