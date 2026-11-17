package ar.edu.uade.reclamos.aplicacion.categoria;

import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Fotos.alta;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerMensajes;
import ar.edu.uade.reclamos.dominio.modelo.Administrador;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MunicipioRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR 26, "Otro problema" de punta a punta con la base real (H2): el alta guarda lo que escribió el vecino y deja
 * el pedido de sugerencia en la outbox; {@link ServicioSugerenciaCategoria} (lo que hace el consumidor de la cola)
 * guarda la sugerencia de la IA; el administrador recategoriza y el reclamo se asigna solo.
 *
 * <p>Como en la reevaluación de prioridad, el broker es un doble, el consumidor no arranca y del otro lado hay un
 * REST 2 falso.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:sugerenciacategoria;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.mensajeria.habilitada=true", "reclamos.mensajeria.intervalo-ms=3600000",
        "reclamos.prioridad.estrategia=ia", "reclamos.prioridad.ia.tiempo-respuesta=500ms",
        "reclamos.prioridad.ia.circuito.llamadas=1000", "reclamos.prioridad.ia.reevaluacion.intentos=2",
        "reclamos.prioridad.ia.reevaluacion.tiempo-respuesta=2s",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SugerenciaCategoriaIntegrationTest {

    private enum Respuesta { VEREDA, NINGUNA, INEXISTENTE, ERROR }

    private static final HttpServer REST2 = iniciarServidor();
    private static final List<String> PEDIDOS_CATEGORIA = new CopyOnWriteArrayList<>();
    private static volatile Respuesta respuesta = Respuesta.ERROR;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired MensajeSalienteRepository mensajes;
    @Autowired ServicioSugerenciaCategoria sugerencias;
    @MockitoBean BrokerMensajes broker;

    private Long municipio;
    private Long otro;
    private Long vereda;
    private Long area;
    private Long ciudadano;
    private Long administrador;

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
        PEDIDOS_CATEGORIA.clear();
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            for (String tabla : List.of("mensajes_salientes", "notificaciones", "historial_estados", "reclamos",
                    "area_categoria", "usuarios", "areas_municipales", "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Categoria otroProblema = categorias.guardar(
                    new Categoria("Otro problema", "Ninguna corresponde", 72, Prioridad.MEDIA, true));
            Categoria veredaRota = categorias.guardar(
                    new Categoria("Vereda rota", "Baldosas sueltas", 168, Prioridad.BAJA));
            AreaMunicipal obras = new AreaMunicipal("Obras Públicas", "obras@example.org", quilmes);
            obras.agregarCategoria(veredaRota);
            Ciudadano ana = (Ciudadano) usuarios.guardar(
                    verificado(new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", "1155550000")));
            Administrador elena = (Administrador) usuarios.guardar(
                    new Administrador("20555666", "Elena", "Ruiz", "elena@example.org", null));
            municipio = quilmes.getId();
            otro = otroProblema.getId();
            vereda = veredaRota.getId();
            area = areas.guardar(obras).getId();
            ciudadano = ana.getId();
            administrador = elena.getId();
        });
    }

    @Test
    void elAltaGuardaLaPropuestaYPideLaSugerenciaSinDatosDelVecino() throws Exception {
        String numero = crearReclamo();

        detalle(numero, ciudadano).andExpect(jsonPath("$.categoria.nombre").value("Otro problema"))
                .andExpect(jsonPath("$.categoriaPropuesta").value("Baldosas levantadas"))
                .andExpect(jsonPath("$.categoriaSugerida").value(nullValue()))
                .andExpect(jsonPath("$.motivoSugerencia").value(nullValue()))
                .andExpect(jsonPath("$.estado").value("INGRESADO"));
        MensajeSaliente pedido = pedidos(TopologiaSugerenciaCategoria.CLAVE).get(0);
        assertThat(pedido.getTipoEvento()).isEqualTo(MensajeSugerenciaCategoria.TIPO);
        JsonNode contenido = json.readTree(pedido.getContenido());
        assertThat(contenido.get("reclamoNumero").asText()).isEqualTo(numero);
        assertThat(contenido.get("intento").asInt()).isEqualTo(1);
        assertThat(pedido.getContenido()).doesNotContain("Baldosas", "Ana", "30111222", "1155550000");
    }

    @Test
    void otroProblemaSinDecirQueEsEs400() throws Exception {
        mvc.perform(alta("""
                        {"categoriaId": %d, "municipioId": %d, "descripcion": "Algo raro en la vereda",
                         "direccion": "Belgrano 450"}""".formatted(otro, municipio)).with(como(ciudadano)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void laSugerenciaDeLaIaQuedaEnElReclamoSinCambiarLaCategoria() throws Exception {
        String numero = crearReclamo();
        respuesta = Respuesta.VEREDA;

        assertThat(sugerencias.sugerir(numero, 1)).isEqualTo(ServicioSugerenciaCategoria.Resultado.SUGERIDA);

        detalle(numero, administrador).andExpect(jsonPath("$.categoria.nombre").value("Otro problema"))
                .andExpect(jsonPath("$.categoriaSugerida.id").value(vereda))
                .andExpect(jsonPath("$.motivoSugerencia").value("Las baldosas sueltas son de la vereda."));
        // A REST 2 le llegan lo que escribió el vecino y las categorías concretas, sin "Otro problema".
        JsonNode enviado = json.readTree(PEDIDOS_CATEGORIA.get(0));
        assertThat(enviado.get("categoriaPropuesta").asText()).isEqualTo("Baldosas levantadas");
        assertThat(enviado.get("categorias").size()).isEqualTo(1);
        assertThat(enviado.get("categorias").get(0).asText()).isEqualTo("Vereda rota");
        assertThat(PEDIDOS_CATEGORIA.get(0)).doesNotContain("Ana", "30111222", "Belgrano");
        // Un mensaje repetido no vuelve a llamar.
        assertThat(sugerencias.sugerir(numero, 1)).isEqualTo(ServicioSugerenciaCategoria.Resultado.SIN_PENDIENTE);
        assertThat(PEDIDOS_CATEGORIA).hasSize(1);
    }

    @Test
    void siLaIaNoEncuentraNingunaQuedaElMotivo() throws Exception {
        String numero = crearReclamo();
        respuesta = Respuesta.NINGUNA;

        sugerencias.sugerir(numero, 1);

        detalle(numero, administrador).andExpect(jsonPath("$.categoriaSugerida").value(nullValue()))
                .andExpect(jsonPath("$.motivoSugerencia").value("No se parece a ninguna categoría."));
    }

    @Test
    void unaCategoriaInventadaSeTrataComoFallaYSeReintenta() throws Exception {
        String numero = crearReclamo();
        respuesta = Respuesta.INEXISTENTE;

        assertThat(sugerencias.sugerir(numero, 1))
                .isEqualTo(ServicioSugerenciaCategoria.Resultado.REINTENTO_PROGRAMADO);
        assertThat(json.readTree(pedidos(TopologiaSugerenciaCategoria.CLAVE_ESPERA).get(0).getContenido())
                .get("intento").asInt()).isEqualTo(2);

        assertThat(sugerencias.sugerir(numero, 2)).isEqualTo(ServicioSugerenciaCategoria.Resultado.ABANDONADA);
        detalle(numero, administrador).andExpect(jsonPath("$.categoriaSugerida").value(nullValue()))
                .andExpect(jsonPath("$.motivoSugerencia")
                        .value("La IA no respondió en 2 intentos: elegí la categoría a mano."));
    }

    @Test
    void elAdministradorRecategorizaYElReclamoSeAsignaSolo() throws Exception {
        String numero = crearReclamo();
        respuesta = Respuesta.VEREDA;
        sugerencias.sugerir(numero, 1);

        mvc.perform(patch("/api/reclamos/" + numero + "/categoria").with(como(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\": %d, \"observacion\": \"Como sugirió la IA\"}".formatted(vereda)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.id").value(vereda))
                .andExpect(jsonPath("$.estado").value("ASIGNADO"))
                .andExpect(jsonPath("$.area.id").value(area))
                .andExpect(jsonPath("$.categoriaPropuesta").value("Baldosas levantadas"))
                .andExpect(jsonPath("$.historial[1].observacion")
                        .value("Categoría: Otro problema → Vereda rota. Como sugirió la IA"));
        assertThat(jdbc.queryForList("select mensaje from notificaciones", String.class))
                .contains("Revisamos tu reclamo " + numero + ": paso de Otro problema a Vereda rota.");
        assertThat(pedidos("aviso.reclamo-recategorizado")).hasSize(1);
        assertThat(pedidos("aviso.reclamo-asignado")).isNotEmpty();
    }

    @Test
    void soloElAdministradorRecategorizaYNoAOtroProblema() throws Exception {
        String numero = crearReclamo();
        String ruta = "/api/reclamos/" + numero + "/categoria";

        mvc.perform(patch(ruta).with(como(ciudadano)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\": %d}".formatted(vereda)))
                .andExpect(status().isForbidden());
        mvc.perform(patch(ruta).with(como(administrador)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\": %d}".formatted(otro)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        mvc.perform(patch(ruta).with(como(administrador)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\": 999999}"))
                .andExpect(status().isNotFound());
    }

    private String crearReclamo() throws Exception {
        String cuerpo = mvc.perform(alta("""
                        {"categoriaId": %d, "municipioId": %d, "descripcion": "Baldosas sueltas frente a la escuela",
                         "direccion": "Belgrano 450", "categoriaPropuesta": "Baldosas levantadas"}"""
                        .formatted(otro, municipio)).with(como(ciudadano)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cuerpo).get("numero").asText();
    }

    private ResultActions detalle(String numero, Long usuario) throws Exception {
        return mvc.perform(get("/api/reclamos/" + numero).with(como(usuario))).andExpect(status().isOk());
    }

    private List<MensajeSaliente> pedidos(String claveRuteo) {
        return mensajes.buscarPendientes(100).stream()
                .filter(mensaje -> mensaje.getClaveRuteo().equals(claveRuteo))
                .toList();
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool(tarea -> {
                Thread hilo = new Thread(tarea, "rest2-falso-categoria");
                hilo.setDaemon(true);
                return hilo;
            }));
            servidor.createContext("/api/prioridades/evaluar", intercambio -> responder(intercambio, 504,
                    "{\"status\": 504, \"codigo\": \"TIEMPO_AGOTADO\", \"mensaje\": \"Sin respuesta\"}"));
            servidor.createContext("/api/categorias/sugerir", SugerenciaCategoriaIntegrationTest::categoria);
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void categoria(HttpExchange intercambio) throws IOException {
        PEDIDOS_CATEGORIA.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String datos = ", \"proveedor\": \"simulado\", \"modelo\": \"reglas\"}";
        switch (respuesta) {
            case VEREDA -> responder(intercambio, 200,
                    "{\"categoria\": \"Vereda rota\", \"motivo\": \"Las baldosas sueltas son de la vereda.\"" + datos);
            case NINGUNA -> responder(intercambio, 200,
                    "{\"categoria\": null, \"motivo\": \"No se parece a ninguna categoría.\"" + datos);
            case INEXISTENTE -> responder(intercambio, 200,
                    "{\"categoria\": \"Fuga de gas\", \"motivo\": \"Inventada.\"" + datos);
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
