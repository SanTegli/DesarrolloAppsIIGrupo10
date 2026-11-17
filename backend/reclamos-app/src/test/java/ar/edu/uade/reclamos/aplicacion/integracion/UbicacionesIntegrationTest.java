package ar.edu.uade.reclamos.aplicacion.integracion;

import static ar.edu.uade.reclamos.aplicacion.Fotos.alta;
import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MunicipioRepository;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
 * Buscar la dirección y crear el reclamo con la ubicación elegida, de punta a punta: REST, fachada,
 * {@code ServicioUbicaciones}, la caché, {@link GeorefGeocodificador} y HTTP reales. Del otro lado hay un
 * servidor mínimo que contesta las respuestas grabadas de Georef, así el test no depende de Internet.
 *
 * <p>Cubre los casos del PR 11 (dirección válida, ambigua, inexistente y Georef caído) y los del PR 25: la
 * búsqueda en cada municipio y la confirmación del municipio con la ubicación inversa.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ubicaciones;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.geocodificacion.tiempo-respuesta=500ms", "reclamos.geocodificacion.confirmar-municipio=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UbicacionesIntegrationTest {

    private static final List<String> CONSULTAS = new CopyOnWriteArrayList<>();
    private static final HttpServer GEOREF = iniciarServidor();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;

    private Long municipio;
    private Long categoria;
    private Long ciudadano;

    @DynamicPropertySource
    static void direccionDeGeoref(DynamicPropertyRegistry propiedades) {
        propiedades.add("reclamos.geocodificacion.url", () -> "http://localhost:" + GEOREF.getAddress().getPort());
    }

    @AfterAll
    static void apagarServidor() {
        GEOREF.stop(0);
    }

    @BeforeEach
    void escenario() {
        CONSULTAS.clear();
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            for (String tabla : List.of("notificaciones", "historial_estados", "reclamos", "area_categoria",
                    "usuarios", "areas_municipales", "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            municipios.guardar(new Municipio("Avellaneda", "Buenos Aires"));
            Categoria bache = categorias.guardar(new Categoria("Bache", "Pozo en la calzada", 120, Prioridad.BAJA));
            AreaMunicipal obras = new AreaMunicipal("Obras Públicas", "obras@example.org", quilmes);
            obras.agregarCategoria(bache);
            areas.guardar(obras);
            Ciudadano ana = (Ciudadano) usuarios.guardar(
                    verificado(new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null)));
            municipio = quilmes.getId();
            categoria = bache.getId();
            ciudadano = ana.getId();
        });
    }

    @Test
    void buscaLaDireccionEnCadaMunicipioYDevuelveLasOpcionesConSuMunicipio() throws Exception {
        sugerencias("Belgrano 450").andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.sugerencias", hasSize(1)))
                .andExpect(jsonPath("$.sugerencias[0].direccionNormalizada")
                        .value("GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires"))
                .andExpect(jsonPath("$.sugerencias[0].latitud").value(-34.73614578))
                .andExpect(jsonPath("$.sugerencias[0].municipio.id").value(municipio))
                .andExpect(jsonPath("$.sugerencias[0].municipio.nombre").value("Quilmes"));
        assertThat(CONSULTAS).hasSize(2).allSatisfy(consulta -> assertThat(consulta)
                .startsWith("/direcciones?").contains("direccion=Belgrano 450", "provincia=Buenos Aires"));
        assertThat(CONSULTAS).anySatisfy(consulta -> assertThat(consulta).contains("departamento=Quilmes"))
                .anySatisfy(consulta -> assertThat(consulta).contains("departamento=Avellaneda"));
    }

    @Test
    void laMismaBusquedaRepetidaNoVuelveAConsultarGeoref() throws Exception {
        sugerencias("Pasaje Repetido 10").andExpect(status().isOk());
        sugerencias("pasaje repetido 10").andExpect(status().isOk());

        assertThat(CONSULTAS).hasSize(2);
    }

    @Test
    void direccionInexistenteDevuelveListaVacia() throws Exception {
        sugerencias("Calle Inventada 123").andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.sugerencias", hasSize(0)));
    }

    @Test
    void georefCaidoDevuelveNoDisponibleSinError() throws Exception {
        sugerencias("Calle Caida 1").andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.sugerencias", hasSize(0)));
    }

    @Test
    void validaLosParametrosDeLaBusqueda() throws Exception {
        sugerencias("ab").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        assertThat(CONSULTAS).isEmpty();
    }

    @Test
    void elAltaConLaSugerenciaElegidaLaConfirmaConGeorefYLaGuarda() throws Exception {
        crear(municipio, """
                , "ubicacion": {"latitud": -34.73614578, "longitud": -58.3228998575,
                  "direccionNormalizada": "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.municipio.nombre").value("Quilmes"))
                .andExpect(jsonPath("$.ubicacion.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.ubicacion.latitud").value(-34.73614578))
                .andExpect(jsonPath("$.ubicacion.direccionNormalizada")
                        .value("GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires"))
                .andExpect(jsonPath("$.ubicacion.proveedor").value("georef"))
                .andExpect(jsonPath("$.direccion").value("Belgrano 450"));
        assertThat(CONSULTAS).singleElement().asString().startsWith("/ubicacion?").contains("lat=-34.73614578");
    }

    @Test
    void elAltaSinUbicacionLaDejaPendienteSinConsultarGeoref() throws Exception {
        crear(municipio, "").andExpect(status().isCreated())
                .andExpect(jsonPath("$.ubicacion.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.ubicacion.latitud").value(nullValue()));
        assertThat(CONSULTAS).isEmpty();
    }

    @Test
    void elAltaConUnaUbicacionFueraDelMunicipioOIncompletaSeRechaza() throws Exception {
        crear(municipio, """
                , "ubicacion": {"latitud": -31.41, "longitud": -64.18, "direccionNormalizada": "BELGRANO 450, Córdoba"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La ubicación elegida queda fuera de Quilmes."));
        crear(municipio, """
                , "ubicacion": {"latitud": -34.7, "direccionNormalizada": "Sin longitud"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void siGeorefUbicaElPuntoEnOtroMunicipioElAltaSeRechaza() throws Exception {
        // Dentro del recuadro de Quilmes, pero del lado de Avellaneda: solo Georef lo sabe.
        crear(municipio, """
                , "ubicacion": {"latitud": -34.67, "longitud": -58.33, "direccionNormalizada": "AV MITRE 5000, Wilde"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La ubicación elegida queda en Avellaneda, no en Quilmes."));
    }

    @Test
    void siLaUbicacionInversaNoRespondeAlcanzaConElRecuadro() throws Exception {
        crear(municipio, """
                , "ubicacion": {"latitud": -34.75, "longitud": -58.25, "direccionNormalizada": "CALLE 1, Quilmes"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ubicacion.estado").value("CONFIRMADA"));
    }

    @Test
    void elAltaConUnMunicipioQueNoExisteEs404() throws Exception {
        crear(Long.MAX_VALUE, "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    private ResultActions sugerencias(String direccion) throws Exception {
        return mvc.perform(get("/api/ubicaciones/sugerencias").with(como(ciudadano)).param("direccion", direccion));
    }

    private ResultActions crear(Long municipioId, String ubicacion) throws Exception {
        return mvc.perform(alta("""
                        {"categoriaId": %d, "municipioId": %d, "descripcion": "Pozo en la calle",
                         "direccion": "Belgrano 450"%s}""".formatted(categoria, municipioId, ubicacion))
                .with(como(ciudadano)));
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool(tarea -> {
                Thread hilo = new Thread(tarea, "georef-falso");
                hilo.setDaemon(true);
                return hilo;
            }));
            servidor.createContext("/direcciones", UbicacionesIntegrationTest::responderDirecciones);
            servidor.createContext("/ubicacion", UbicacionesIntegrationTest::responderUbicacion);
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Belgrano 450 solo existe en Quilmes; "Caida" simula Georef caído. */
    private static void responderDirecciones(HttpExchange intercambio) throws IOException {
        String consulta = anotar(intercambio);
        if (consulta.contains("Caida")) {
            responder(intercambio, 500, "{\"errores\": [{\"mensaje\": \"Error interno\"}]}");
            return;
        }
        String archivo = consulta.contains("Belgrano") && consulta.contains("departamento=Quilmes")
                ? "belgrano-450.json" : "sin-resultados.json";
        try (InputStream grabada = UbicacionesIntegrationTest.class.getResourceAsStream("/georef/" + archivo)) {
            responder(intercambio, 200, new String(grabada.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** -34.67 cae en Avellaneda, -34.75 hace fallar a Georef y el resto cae en Quilmes. */
    private static void responderUbicacion(HttpExchange intercambio) throws IOException {
        String consulta = anotar(intercambio);
        if (consulta.contains("lat=-34.67")) {
            try (InputStream grabada = UbicacionesIntegrationTest.class
                    .getResourceAsStream("/georef/ubicacion-wilde.json")) {
                responder(intercambio, 200, new String(grabada.readAllBytes(), StandardCharsets.UTF_8));
            }
        } else if (consulta.contains("lat=-34.75")) {
            responder(intercambio, 500, "{\"errores\": [{\"mensaje\": \"Error interno\"}]}");
        } else {
            responder(intercambio, 200, "{\"ubicacion\": {\"departamento\": {\"id\": \"06658\", "
                    + "\"nombre\": \"Quilmes\"}, \"provincia\": {\"id\": \"06\", \"nombre\": \"Buenos Aires\"}}}");
        }
    }

    private static String anotar(HttpExchange intercambio) {
        String consulta = intercambio.getRequestURI().getPath() + "?"
                + URLDecoder.decode(intercambio.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
        CONSULTAS.add(consulta);
        return consulta;
    }

    private static void responder(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}
