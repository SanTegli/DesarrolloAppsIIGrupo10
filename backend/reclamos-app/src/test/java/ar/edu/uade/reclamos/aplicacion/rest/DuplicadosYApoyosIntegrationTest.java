package ar.edu.uade.reclamos.aplicacion.rest;

import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Fotos.alta;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.sinCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Posibles duplicados (reclamos abiertos de la misma categoría cerca del punto elegido) y apoyos, por la API.
 * Ana reclama un farol en Belgrano 450; Bruno, al reclamar lo mismo a 20 metros, lo encuentra y lo apoya.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:duplicados;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.uso.reclamos-por-dia=20"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DuplicadosYApoyosIntegrationTest {

    private static final double LATITUD = -34.7361;
    private static final double LONGITUD = -58.3229;
    /** Unos 20 metros al norte. */
    private static final double LATITUD_VECINA = LATITUD + 0.00018;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReclamoRepository reclamos;

    private record Datos(Long ana, Long bruno, Long laura, Long carla, Long elena, Long luminaria, Long bache,
                         Long barrio) { }

    private Datos datos;

    @BeforeEach
    void escenario() {
        datos = new TransactionTemplate(transacciones).execute(tx -> {
            for (String tabla : List.of("apoyos", "codigos_verificacion", "notificaciones", "historial_estados",
                    "reclamos", "area_barrio", "area_categoria", "usuarios", "areas_municipales", "barrios",
                    "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Barrio bernal = barrios.guardar(new Barrio("Bernal", quilmes));
            Categoria luminaria = categorias.guardar(new Categoria("Luminaria rota", "Farol", 48, Prioridad.MEDIA));
            Categoria bache = categorias.guardar(new Categoria("Bache", "Pozo", 120, Prioridad.BAJA));
            AreaMunicipal alumbrado = new AreaMunicipal("Alumbrado", "alumbrado@example.org", quilmes);
            alumbrado.agregarBarrio(bernal);
            alumbrado.agregarCategoria(luminaria);
            alumbrado = areas.guardar(alumbrado);
            Usuario ana = usuarios.guardar(verificado(new Ciudadano("30111222", "Ana", "Perez", "ana@example.org", null)));
            Usuario bruno = usuarios.guardar(verificado(new Ciudadano("31222333", "Bruno", "Diaz", "bruno@example.org",
                    null)));
            Usuario laura = usuarios.guardar(new Ciudadano("33444555", "Laura", "Martinez", "laura@example.org", null));
            Usuario carla = usuarios.guardar(new AgenteMunicipal("25333444", "Carla", "Gomez", "carla@example.org",
                    null, alumbrado));
            Usuario elena = usuarios.guardar(new Administrador("20555666", "Elena", "Ruiz", "elena@example.org", null));
            return new Datos(ana.getId(), bruno.getId(), laura.getId(), carla.getId(), elena.getId(),
                    luminaria.getId(), bache.getId(), bernal.getId());
        });
    }

    @Test
    void encuentraElReclamoAbiertoDeLaMismaCategoriaAPocosMetrosSinDatosDelAutor() throws Exception {
        String numero = crear(datos.ana(), datos.luminaria(), LATITUD, LONGITUD);

        JsonNode candidatos = leer(candidatos(datos.bruno(), datos.luminaria(), LATITUD_VECINA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].numero").value(numero))
                .andExpect(jsonPath("$[0].distanciaMetros").value(20))
                .andExpect(jsonPath("$[0].estado").value("ASIGNADO"))
                .andExpect(jsonPath("$[0].foto").value("/api/reclamos/" + numero + "/foto"))
                .andExpect(jsonPath("$[0].cantidadApoyos").value(0))
                .andExpect(jsonPath("$[0].apoyadoPorMi").value(false))
                .andExpect(jsonPath("$[0].propio").value(false)));
        List<String> campos = new java.util.ArrayList<>();
        candidatos.get(0).fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("numero", "estado", "fechaCreacion", "descripcion",
                "direccion", "foto", "distanciaMetros", "cantidadApoyos", "apoyadoPorMi", "propio");
        assertThat(candidatos.toString()).doesNotContain("Ana", "Perez", "ana@example.org", "30111222");

        candidatos(datos.ana(), datos.luminaria(), LATITUD_VECINA).andExpect(jsonPath("$[0].propio").value(true));
        candidatos(datos.carla(), datos.luminaria(), LATITUD_VECINA).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void otraCategoriaMasDeCincuentaMetrosOSinUbicacionNoSonCandidatos() throws Exception {
        crear(datos.ana(), datos.luminaria(), LATITUD, LONGITUD);
        crearSinUbicacion(datos.ana(), datos.luminaria());
        String lejos = crear(datos.ana(), datos.luminaria(), LATITUD + 0.0027, LONGITUD); // unos 300 m

        candidatos(datos.bruno(), datos.bache(), LATITUD_VECINA).andExpect(jsonPath("$", hasSize(0)));
        candidatos(datos.bruno(), datos.luminaria(), LATITUD_VECINA).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/reclamos/candidatos-duplicados").with(como(datos.bruno()))
                        .param("categoriaId", datos.luminaria().toString())
                        .param("latitud", String.valueOf(LATITUD_VECINA)).param("longitud", String.valueOf(LONGITUD))
                        .param("radio", "200"))
                .andExpect(jsonPath("$", hasSize(1)));
        candidatos(datos.bruno(), datos.luminaria(), LATITUD + 0.0027)
                .andExpect(jsonPath("$[0].numero").value(lejos));
    }

    @Test
    void losParametrosTienenLimites() throws Exception {
        mvc.perform(get("/api/reclamos/candidatos-duplicados").with(como(datos.bruno()))
                        .param("categoriaId", datos.luminaria().toString()).param("latitud", "-34.7")
                        .param("longitud", "-58.3").param("radio", "500"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mvc.perform(get("/api/reclamos/candidatos-duplicados").with(como(datos.bruno()))
                        .param("categoriaId", datos.luminaria().toString()).param("latitud", "-95")
                        .param("longitud", "-58.3"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reclamos/candidatos-duplicados").with(como(datos.bruno()))
                        .param("categoriaId", "999999").param("latitud", "-34.7").param("longitud", "-58.3"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/reclamos/candidatos-duplicados")
                        .param("categoriaId", datos.luminaria().toString()).param("latitud", "-34.7")
                        .param("longitud", "-58.3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apoyarYRetirarSonIdempotentesYNoDanAccesoAlDetalle() throws Exception {
        String numero = crear(datos.ana(), datos.luminaria(), LATITUD, LONGITUD);
        String prioridad = leer(mvc.perform(get("/api/reclamos/" + numero).with(como(datos.ana()))))
                .get("prioridad").asText();

        apoyo(post(ruta(numero)), datos.bruno()).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadApoyos").value(1)).andExpect(jsonPath("$.apoyado").value(true));
        apoyo(post(ruta(numero)), datos.bruno()).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadApoyos").value(1));
        candidatos(datos.bruno(), datos.luminaria(), LATITUD_VECINA)
                .andExpect(jsonPath("$[0].apoyadoPorMi").value(true))
                .andExpect(jsonPath("$[0].cantidadApoyos").value(1));
        mvc.perform(get("/api/reclamos/" + numero).with(como(datos.ana())))
                .andExpect(jsonPath("$.cantidadApoyos").value(1))
                .andExpect(jsonPath("$.prioridad").value(prioridad));
        mvc.perform(get("/api/reclamos/" + numero).with(como(datos.bruno()))).andExpect(status().isForbidden());
        // La foto sí: es la que muestra la tarjeta del posible duplicado.
        mvc.perform(get("/api/reclamos/" + numero + "/foto").with(como(datos.bruno()))).andExpect(status().isOk());

        apoyo(delete(ruta(numero)), datos.bruno()).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadApoyos").value(0)).andExpect(jsonPath("$.apoyado").value(false));
        apoyo(delete(ruta(numero)), datos.bruno()).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadApoyos").value(0));
        assertThat(jdbc.queryForObject("select count(*) from apoyos", Long.class)).isZero();
    }

    @Test
    void reglasDelApoyo() throws Exception {
        String numero = crear(datos.ana(), datos.luminaria(), LATITUD, LONGITUD);

        apoyo(post(ruta(numero)), datos.ana()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("No podés apoyar tu propio reclamo."));
        apoyo(post(ruta(numero)), datos.carla()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        apoyo(post(ruta(numero)), datos.laura()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_NO_VERIFICADO"));
        apoyo(post(ruta("REC-00000000")), datos.bruno()).andExpect(status().isNotFound());
        mvc.perform(post(ruta(numero)).with(sinCsrf(datos.bruno()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));
        mvc.perform(delete(ruta(numero)).with(sinCsrf(datos.bruno()))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from apoyos", Long.class)).isZero();
    }

    @Test
    void unReclamoResueltoNoSeApoyaNiApareceYSuFotoVuelveASerPrivada() throws Exception {
        String numero = crear(datos.ana(), datos.luminaria(), LATITUD, LONGITUD);
        apoyo(post(ruta(numero)), datos.bruno()).andExpect(status().isOk());
        cambiarEstado(datos.carla(), numero, "EN_PROCESO");
        cambiarEstado(datos.carla(), numero, "RESUELTO");

        candidatos(datos.bruno(), datos.luminaria(), LATITUD_VECINA).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/reclamos/" + numero + "/foto").with(como(datos.bruno())))
                .andExpect(status().isForbidden());
        // Retirar el apoyo se puede aunque ya esté resuelto; volver a apoyar, no.
        apoyo(delete(ruta(numero)), datos.bruno()).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadApoyos").value(0));
        apoyo(post(ruta(numero)), datos.bruno()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
    }

    @Test
    void unaCuentaBloqueadaNoApoya() throws Exception {
        String numero = crear(datos.ana(), datos.luminaria(), LATITUD, LONGITUD);
        mvc.perform(patch("/api/usuarios/" + datos.bruno() + "/bloqueo").with(como(datos.elena()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"bloqueado\": true}")).andExpect(status().isOk());

        apoyo(post(ruta(numero)), datos.bruno()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
    }

    private String crear(Long usuario, Long categoria, double latitud, double longitud) throws Exception {
        String cuerpo = """
                {"categoriaId": %d, "barrioId": %d, "descripcion": "Farol apagado", "direccion": "Belgrano 450",
                 "ubicacion": {"latitud": %s, "longitud": %s, "direccionNormalizada": "GRL BELGRANO 450, Bernal"}}"""
                .formatted(categoria, datos.barrio(), latitud, longitud);
        return leer(mvc.perform(alta(cuerpo).with(como(usuario))).andExpect(status().isCreated()))
                .get("numero").asText();
    }

    private void crearSinUbicacion(Long usuario, Long categoria) throws Exception {
        mvc.perform(alta("""
                {"categoriaId": %d, "barrioId": %d, "descripcion": "Farol", "direccion": "Belgrano 450"}"""
                .formatted(categoria, datos.barrio())).with(como(usuario))).andExpect(status().isCreated());
    }

    private ResultActions candidatos(Long usuario, Long categoria, double latitud) throws Exception {
        return mvc.perform(get("/api/reclamos/candidatos-duplicados").with(como(usuario))
                .param("categoriaId", categoria.toString())
                .param("latitud", String.valueOf(latitud)).param("longitud", String.valueOf(LONGITUD)));
    }

    private ResultActions apoyo(MockHttpServletRequestBuilder pedido, Long usuario) throws Exception {
        return mvc.perform(pedido.with(como(usuario)));
    }

    private void cambiarEstado(Long usuario, String numero, String estado) throws Exception {
        mvc.perform(patch("/api/reclamos/" + numero + "/estado").with(como(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\": \"" + estado + "\"}"))
                .andExpect(status().isOk());
    }

    private static String ruta(String numero) {
        return "/api/reclamos/" + numero + "/apoyo";
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
