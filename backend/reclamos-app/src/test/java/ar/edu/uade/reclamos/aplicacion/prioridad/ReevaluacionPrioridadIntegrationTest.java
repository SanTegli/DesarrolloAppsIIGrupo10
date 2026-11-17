package ar.edu.uade.reclamos.aplicacion.prioridad;

import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Fotos.alta;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerMensajes;
import ar.edu.uade.reclamos.dominio.modelo.AgenteMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.BarrioRepository;
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
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La IA que no responde en el alta, de punta a punta con la base real (H2): el reclamo se crea con la
 * prioridad de la categoría y origen {@code PENDIENTE_IA}, el pedido de reevaluación queda en la outbox en la
 * misma transacción, y {@link ServicioReevaluacionPrioridad} (lo que hace el consumidor de la cola) completa la
 * prioridad, reintenta o se rinde.
 *
 * <p>El broker es un doble y el consumidor no arranca ({@code auto-startup=false}): acá se llama al servicio
 * directamente, como lo haría el listener con cada mensaje. Del otro lado hay un REST 2 falso.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:reevaluacion;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.mensajeria.habilitada=true", "reclamos.mensajeria.intervalo-ms=3600000",
        "reclamos.prioridad.estrategia=ia", "reclamos.prioridad.ia.tiempo-respuesta=500ms",
        "reclamos.prioridad.ia.circuito.llamadas=1000", "reclamos.prioridad.ia.reevaluacion.intentos=2",
        "reclamos.prioridad.ia.reevaluacion.tiempo-respuesta=2s",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReevaluacionPrioridadIntegrationTest {

    private enum Respuesta { CRITICA, ERROR }

    private static final HttpServer REST2 = iniciarServidor();
    private static volatile Respuesta respuesta = Respuesta.ERROR;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired MensajeSalienteRepository mensajes;
    @Autowired ServicioReevaluacionPrioridad reevaluacion;
    @MockitoBean BrokerMensajes broker;

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
        respuesta = Respuesta.ERROR;
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            for (String tabla : List.of("mensajes_salientes", "notificaciones", "historial_estados", "reclamos",
                    "area_barrio", "area_categoria", "usuarios", "areas_municipales", "barrios", "categorias",
                    "municipios")) {
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
            usuarios.guardar(new AgenteMunicipal("30333444", "Carla", "Gómez", "carla@example.org", null, alumbrado));
            barrio = bernal.getId();
            categoria = luminaria.getId();
            ciudadano = ana.getId();
        });
    }

    @Test
    void siLaIaNoRespondeElReclamoQuedaPendienteYElPedidoVaALaOutbox() throws Exception {
        String numero = crearReclamo();

        detalle(numero).andExpect(jsonPath("$.prioridad").value("MEDIA"))
                .andExpect(jsonPath("$.origenPrioridad").value("PENDIENTE_IA"));
        MensajeSaliente pedido = pedidos(TopologiaReevaluacion.CLAVE).get(0);
        assertThat(pedido.getTipoEvento()).isEqualTo(MensajeReevaluacion.TIPO);
        JsonNode contenido = json.readTree(pedido.getContenido());
        assertThat(contenido.get("reclamoNumero").asText()).isEqualTo(numero);
        assertThat(contenido.get("intento").asInt()).isEqualTo(1);
        assertThat(contenido.get("version").asInt()).isEqualTo(MensajeReevaluacion.VERSION);
        // Ni la descripción ni datos del vecino viajan por la cola: el consumidor los lee de la base.
        assertThat(pedido.getContenido()).doesNotContain("Cables", "Ana", "30111222", "1155550000");
    }

    @Test
    void cuandoLaIaRespondeSubeLaPrioridadYAvisaAlVecinoYAlArea() throws Exception {
        String numero = crearReclamo();
        respuesta = Respuesta.CRITICA;

        assertThat(reevaluacion.reevaluar(numero, 1)).isEqualTo(ServicioReevaluacionPrioridad.Resultado.REVISADA);

        detalle(numero).andExpect(jsonPath("$.prioridad").value("CRITICA"))
                .andExpect(jsonPath("$.origenPrioridad").value("IA"))
                .andExpect(jsonPath("$.motivoPrioridad").value("Cables expuestos: riesgo eléctrico."));
        assertThat(jdbc.queryForList("select mensaje from notificaciones", String.class))
                .contains("La IA reviso tu reclamo " + numero + ": la prioridad paso de MEDIA a CRITICA.",
                        "La IA subio la prioridad del reclamo " + numero + " de MEDIA a CRITICA.");
        assertThat(pedidos("aviso.prioridad-revisada")).hasSize(2);
    }

    @Test
    void unPedidoRepetidoNoVuelveALlamarNiAvisa() throws Exception {
        String numero = crearReclamo();
        respuesta = Respuesta.CRITICA;
        reevaluacion.reevaluar(numero, 1);
        int avisos = jdbc.queryForObject("select count(*) from notificaciones", Integer.class);

        assertThat(reevaluacion.reevaluar(numero, 1))
                .isEqualTo(ServicioReevaluacionPrioridad.Resultado.SIN_PENDIENTE);
        assertThat(jdbc.queryForObject("select count(*) from notificaciones", Integer.class)).isEqualTo(avisos);
    }

    @Test
    void siLaIaSigueSinResponderReintentaYDespuesQuedaLaCategoria() throws Exception {
        String numero = crearReclamo();

        assertThat(reevaluacion.reevaluar(numero, 1))
                .isEqualTo(ServicioReevaluacionPrioridad.Resultado.REINTENTO_PROGRAMADO);
        MensajeSaliente reintento = pedidos(TopologiaReevaluacion.CLAVE_ESPERA).get(0);
        assertThat(json.readTree(reintento.getContenido()).get("intento").asInt()).isEqualTo(2);
        detalle(numero).andExpect(jsonPath("$.origenPrioridad").value("PENDIENTE_IA"));

        // Con intentos=2, el segundo es el último.
        assertThat(reevaluacion.reevaluar(numero, 2)).isEqualTo(ServicioReevaluacionPrioridad.Resultado.ABANDONADA);
        detalle(numero).andExpect(jsonPath("$.prioridad").value("MEDIA"))
                .andExpect(jsonPath("$.origenPrioridad").value("RESPALDO"));
        assertThat(pedidos(TopologiaReevaluacion.CLAVE_ESPERA)).hasSize(1);
    }

    @Test
    void siLaIaRespondeEnElAltaNoSePideNingunaReevaluacion() throws Exception {
        respuesta = Respuesta.CRITICA;

        String numero = crearReclamo();

        detalle(numero).andExpect(jsonPath("$.origenPrioridad").value("IA"));
        assertThat(pedidos(TopologiaReevaluacion.CLAVE)).isEmpty();
    }

    @Test
    void unReclamoQueNoExisteSeDescarta() {
        assertThat(reevaluacion.reevaluar("REC-00000000", 1))
                .isEqualTo(ServicioReevaluacionPrioridad.Resultado.RECLAMO_INEXISTENTE);
    }

    private String crearReclamo() throws Exception {
        String cuerpo = mvc.perform(alta("""
                        {"categoriaId": %d, "barrioId": %d, "descripcion": "Cables colgando del poste",
                         "direccion": "Belgrano 450"}""".formatted(categoria, barrio)).with(como(ciudadano)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cuerpo).get("numero").asText();
    }

    private org.springframework.test.web.servlet.ResultActions detalle(String numero) throws Exception {
        return mvc.perform(get("/api/reclamos/" + numero).with(como(ciudadano))).andExpect(status().isOk());
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
                Thread hilo = new Thread(tarea, "rest2-falso-reevaluacion");
                hilo.setDaemon(true);
                return hilo;
            }));
            servidor.createContext("/api/prioridades/evaluar", ReevaluacionPrioridadIntegrationTest::responder);
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void responder(HttpExchange intercambio) throws IOException {
        intercambio.getRequestBody().readAllBytes();
        boolean bien = respuesta == Respuesta.CRITICA;
        String cuerpo = bien
                ? "{\"prioridad\": \"CRITICA\", \"motivo\": \"Cables expuestos: riesgo eléctrico.\", "
                        + "\"proveedor\": \"gemini\", \"modelo\": \"gemini-3.5-flash-lite\"}"
                : "{\"status\": 504, \"codigo\": \"TIEMPO_AGOTADO\", \"mensaje\": \"Gemini no respondió a tiempo\"}";
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(bien ? 200 : 504, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
