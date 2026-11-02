package ar.edu.uade.reclamos.aplicacion.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * <p>Cubre los casos del PR 11: dirección válida, ambigua, inexistente y Georef caído.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ubicaciones;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.geocodificacion.tiempo-respuesta=500ms"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UbicacionesIntegrationTest {

    private static final List<String> CONSULTAS = new CopyOnWriteArrayList<>();
    private static final HttpServer GEOREF = iniciarServidor();

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
            for (String tabla : List.of("notificaciones", "historial_estados", "reclamos", "area_barrio",
                    "area_categoria", "usuarios", "areas_municipales", "barrios", "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Barrio bernal = barrios.guardar(new Barrio("Bernal", quilmes));
            Categoria bache = categorias.guardar(new Categoria("Bache", "Pozo en la calzada", 120, Prioridad.BAJA));
            AreaMunicipal obras = new AreaMunicipal("Obras Públicas", "obras@example.org", quilmes);
            obras.agregarBarrio(bernal);
            obras.agregarCategoria(bache);
            areas.guardar(obras);
            Ciudadano ana = (Ciudadano) usuarios.guardar(
                    new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null));
            barrio = bernal.getId();
            categoria = bache.getId();
            ciudadano = ana.getId();
        });
    }

    @Test
    void direccionAmbiguaDevuelveSoloLasOpcionesConCoordenadas() throws Exception {
        sugerencias("Belgrano 450").andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.sugerencias", hasSize(1)))
                .andExpect(jsonPath("$.sugerencias[0].direccionNormalizada")
                        .value("GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires"))
                .andExpect(jsonPath("$.sugerencias[0].latitud").value(-34.73614578));
        assertThat(CONSULTAS).singleElement().asString()
                .contains("direccion=Belgrano 450", "departamento=Quilmes", "provincia=Buenos Aires");
    }

    @Test
    void laMismaBusquedaRepetidaNoVuelveAConsultarGeoref() throws Exception {
        sugerencias("Pasaje Repetido 10").andExpect(status().isOk());
        sugerencias("pasaje repetido 10").andExpect(status().isOk());

        assertThat(CONSULTAS).hasSize(1);
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
        mvc.perform(get("/api/ubicaciones/sugerencias").param("direccion", "Belgrano 450").param("barrioId", "999"))
                .andExpect(status().isNotFound());
        assertThat(CONSULTAS).isEmpty();
    }

    @Test
    void elAltaConLaSugerenciaElegidaGuardaLaUbicacionConfirmada() throws Exception {
        crear("""
                , "ubicacion": {"latitud": -34.73614578, "longitud": -58.3228998575,
                  "direccionNormalizada": "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ubicacion.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.ubicacion.latitud").value(-34.73614578))
                .andExpect(jsonPath("$.ubicacion.direccionNormalizada")
                        .value("GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires"))
                .andExpect(jsonPath("$.ubicacion.proveedor").value("georef"))
                .andExpect(jsonPath("$.direccion").value("Belgrano 450"));
    }

    @Test
    void elAltaSinUbicacionLaDejaPendiente() throws Exception {
        crear("").andExpect(status().isCreated())
                .andExpect(jsonPath("$.ubicacion.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.ubicacion.latitud").value(nullValue()));
    }

    @Test
    void elAltaConUnaUbicacionFueraDelMunicipioOIncompletaSeRechaza() throws Exception {
        crear("""
                , "ubicacion": {"latitud": -31.41, "longitud": -64.18, "direccionNormalizada": "BELGRANO 450, Córdoba"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La ubicación elegida queda fuera de Quilmes."));
        crear("""
                , "ubicacion": {"latitud": -34.7, "direccionNormalizada": "Sin longitud"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    private ResultActions sugerencias(String direccion) throws Exception {
        return mvc.perform(get("/api/ubicaciones/sugerencias").param("direccion", direccion)
                .param("barrioId", barrio.toString()));
    }

    private ResultActions crear(String ubicacion) throws Exception {
        return mvc.perform(post("/api/reclamos").header("X-Usuario-Id", ciudadano)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoriaId": %d, "barrioId": %d, "descripcion": "Pozo en la calle",
                         "direccion": "Belgrano 450"%s}""".formatted(categoria, barrio, ubicacion)));
    }

    private static HttpServer iniciarServidor() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.setExecutor(Executors.newCachedThreadPool(tarea -> {
                Thread hilo = new Thread(tarea, "georef-falso");
                hilo.setDaemon(true);
                return hilo;
            }));
            servidor.createContext("/direcciones", UbicacionesIntegrationTest::responder);
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void responder(HttpExchange intercambio) throws IOException {
        String consulta = URLDecoder.decode(intercambio.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
        CONSULTAS.add(consulta);
        int estado = 200;
        byte[] cuerpo;
        if (consulta.contains("Caida")) {
            estado = 500;
            cuerpo = "{\"errores\": [{\"mensaje\": \"Error interno\"}]}".getBytes(StandardCharsets.UTF_8);
        } else {
            String archivo = consulta.contains("Belgrano") ? "belgrano-450.json" : "sin-resultados.json";
            try (InputStream grabada = UbicacionesIntegrationTest.class.getResourceAsStream("/georef/" + archivo)) {
                cuerpo = grabada.readAllBytes();
            }
        }
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}
