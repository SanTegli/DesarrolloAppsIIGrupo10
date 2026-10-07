package ar.edu.uade.reclamos.aplicacion.rest;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.dominio.repositorio.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** HTTP, Facade, servicios, Observer y persistencia reales, sin transaccion envolviendo el test. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment entorno;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReclamoRepository reclamos;
    @Autowired NotificacionRepository notificaciones;
    @Autowired PlatformTransactionManager transacciones;
    // Solo el caso de error inesperado reemplaza una llamada puntual de la Facade.
    @MockitoSpyBean GestionReclamosFacade facade;

    private record Datos(Long ciudadano, Long otroCiudadano, Long agente, Long otroAgente, Long administrador,
                         Long categoria, Long sinArea, Long barrio, Long area, Long otraArea,
                         String asignado, String ajeno, String ingresado, String resuelto) { }
    private Datos datos;

    @BeforeEach
    void escenarioReal() {
        datos = new TransactionTemplate(transacciones).execute(tx -> {
            for (String tabla : List.of("notificaciones", "historial_estados", "reclamos", "area_barrio",
                    "area_categoria", "usuarios", "areas_municipales", "barrios", "categorias", "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio municipio = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Barrio barrio = barrios.guardar(new Barrio("Bernal", municipio));
            Categoria categoria = categorias.guardar(new Categoria("Bache", "Pozo urbano", 48, Prioridad.ALTA));
            Categoria sinArea = categorias.guardar(new Categoria("Sin cobertura", "Prueba", 24, Prioridad.BAJA));
            AreaMunicipal area = new AreaMunicipal("Obras", "obras@test.org", municipio);
            AreaMunicipal otraArea = new AreaMunicipal("Vial", "vial@test.org", municipio);
            for (AreaMunicipal candidata : List.of(area, otraArea)) {
                candidata.agregarCategoria(categoria);
                candidata.agregarBarrio(barrio);
                areas.guardar(candidata);
            }
            Ciudadano ciudadano = (Ciudadano) usuarios.guardar(new Ciudadano("30111222", "Ana", "Perez", "ana@test.org", null));
            Ciudadano otro = (Ciudadano) usuarios.guardar(new Ciudadano("30222333", "Luis", "Diaz", "luis@test.org", null));
            AgenteMunicipal agente = (AgenteMunicipal) usuarios.guardar(new AgenteMunicipal("30333444", "Carla", "Gomez", "carla@test.org", null, area));
            AgenteMunicipal otroAgente = (AgenteMunicipal) usuarios.guardar(new AgenteMunicipal("30444555", "Diego", "Sosa", "diego@test.org", null, otraArea));
            Usuario administrador = usuarios.guardar(new Administrador("30555666", "Elena", "Ruiz", "elena@test.org", null));
            Reclamo asignado = crearFixture(ciudadano, categoria, barrio, 1);
            Reclamo ajeno = crearFixture(otro, categoria, barrio, 2);
            Reclamo ingresado = crearFixture(ciudadano, sinArea, barrio, 3);
            Reclamo resuelto = crearFixture(otro, categoria, barrio, 4);
            LocalDateTime fecha = LocalDateTime.of(2026, 1, 5, 12, 0);
            asignado.asignarArea(area, administrador, "Manual", fecha);
            ajeno.asignarArea(otraArea, administrador, "Manual", fecha);
            resuelto.asignarArea(area, administrador, "Manual", fecha);
            resuelto.cambiarEstado(EstadoReclamo.EN_PROCESO, agente, "Tomar", fecha);
            resuelto.cambiarEstado(EstadoReclamo.RESUELTO, agente, "Resuelto", fecha);
            List.of(asignado, ajeno, ingresado, resuelto).forEach(reclamos::guardar);
            return new Datos(ciudadano.getId(), otro.getId(), agente.getId(), otroAgente.getId(), administrador.getId(),
                    categoria.getId(), sinArea.getId(), barrio.getId(), area.getId(), otraArea.getId(),
                    asignado.getNumero(), ajeno.getNumero(), ingresado.getNumero(), resuelto.getNumero());
        });
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(entorno.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }

    @AfterEach
    void restaurarFacade() {
        reset(facade);
    }

    @Test
    void postValidoDevuelve201LocationDtoCompletoYNotificaciones() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/reclamos").header("X-Usuario-Id", datos.ciudadano())
                        .contentType(MediaType.APPLICATION_JSON).content(creacion(datos.categoria())))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estado").value("ASIGNADO"))
                .andExpect(jsonPath("$.accionesDisponibles").value(contains("CANCELADO")))
                .andExpect(jsonPath("$.historial", hasSize(2)))
                .andExpect(jsonPath("$.agente").value(nullValue()))
                .andExpect(jsonPath("$.historial[1].usuario").value(nullValue()))
                .andReturn();
        JsonNode dto = leer(resultado);
        assertThat(resultado.getResponse().getHeader("Location")).isEqualTo("/api/reclamos/" + dto.get("numero").asText());
        assertThat(dto.get("numero").asText()).matches("REC-[0-9A-F]{8}");
        campos(dto, "numero", "descripcion", "direccion", "estado", "prioridad", "vencido", "fechaCreacion",
                "fechaLimite", "categoria", "barrio", "ciudadano", "area", "agente", "historial", "accionesDisponibles");
        campos(dto.get("ciudadano"), "id", "nombreCompleto");
        campos(dto.get("categoria"), "id", "nombre");
        campos(dto.get("historial").get(0), "estadoAnterior", "estadoNuevo", "fecha", "observacion", "usuario");
        assertThat(LocalDateTime.parse(dto.get("fechaCreacion").asText())).isNotNull();
        // Ingreso y asignación al ciudadano, más el aviso al agente del área asignada.
        assertThat(notificaciones.buscarPorReclamo(dto.get("numero").asText())).hasSize(3);
    }

    @Test
    void postSinAreaCompatibleDevuelveIngresadoYAreaNula() throws Exception {
        mvc.perform(post("/api/reclamos").header("X-Usuario-Id", datos.ciudadano())
                        .contentType(MediaType.APPLICATION_JSON).content(creacion(datos.sinArea())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("INGRESADO"))
                .andExpect(jsonPath("$.area").value(nullValue())).andExpect(jsonPath("$.historial", hasSize(1)));
    }

    @ParameterizedTest
    @MethodSource("cuerposInvalidos")
    void postConBodyInvalidoEs400(String body) throws Exception {
        error(mvc.perform(post("/api/reclamos").header("X-Usuario-Id", datos.ciudadano())
                .contentType(MediaType.APPLICATION_JSON).content(body)), 400, "DATOS_INVALIDOS", "/api/reclamos");
    }

    static Stream<String> cuerposInvalidos() {
        return Stream.of("{}", "null", "{", "{\"categoriaId\":1,\"barrioId\":1,\"descripcion\":\"  \",\"direccion\":\"Calle\"}",
                "{\"categoriaId\":0,\"barrioId\":-1,\"descripcion\":\"Pozo\",\"direccion\":\"Calle\"}",
                "{\"categoriaId\":1.5,\"barrioId\":1,\"descripcion\":\"Pozo\",\"direccion\":\"Calle\"}",
                "{\"categoriaId\":1,\"barrioId\":1,\"descripcion\":\"" + "x".repeat(1001) + "\",\"direccion\":\"Calle\"}",
                "{\"categoriaId\":1,\"barrioId\":1,\"descripcion\":\"Pozo\",\"direccion\":\"" + "x".repeat(201) + "\"}");
    }

    @Test
    void beanValidationDevuelveUnDetallePorCampoInvalido() throws Exception {
        MvcResult resultado = error(mvc.perform(post("/api/reclamos").header("X-Usuario-Id", datos.ciudadano())
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 400, "DATOS_INVALIDOS", "/api/reclamos");
        assertThat(leer(resultado).get("detalles")).hasSize(4);
    }

    @Test
    void postUsuarioInexistenteEs404() throws Exception {
        error(mvc.perform(post("/api/reclamos").header("X-Usuario-Id", Long.MAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON).content(creacion(datos.categoria()))),
                404, "RECURSO_NO_ENCONTRADO", "/api/reclamos");
    }

    @Test
    void postCiudadanoInactivoEs409YNoPersisteReclamo() throws Exception {
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            Usuario ciudadano = usuarios.buscarPorId(datos.ciudadano()).orElseThrow();
            ciudadano.desactivar();
            usuarios.guardar(ciudadano);
        });
        error(mvc.perform(post("/api/reclamos").header("X-Usuario-Id", datos.ciudadano())
                .contentType(MediaType.APPLICATION_JSON).content(creacion(datos.categoria()))),
                409, "REGLA_NEGOCIO", "/api/reclamos");
        assertThat(reclamos.buscarTodos()).hasSize(4);
        assertThat(notificaciones.buscarPorDestinatario(datos.ciudadano())).isEmpty();
    }

    @Test
    void postAgenteNoPuedeCrearEs403() throws Exception {
        error(mvc.perform(post("/api/reclamos").header("X-Usuario-Id", datos.agente())
                .contentType(MediaType.APPLICATION_JSON).content(creacion(datos.categoria()))),
                403, "ACCESO_DENEGADO", "/api/reclamos");
    }

    @Test
    void todasLasRutasDeReclamosExigenHeader() throws Exception {
        String base = "/api/reclamos/" + datos.asignado();
        for (MockHttpServletRequestBuilder request : List.of(get("/api/reclamos"), get(base), get(base + "/notificaciones"),
                post("/api/reclamos").contentType(MediaType.APPLICATION_JSON).content(creacion(datos.categoria())),
                patch(base + "/estado").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_PROCESO\"}"),
                patch(base + "/asignacion").contentType(MediaType.APPLICATION_JSON).content("{\"areaId\":" + datos.otraArea() + "}"))) {
            mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "texto", "0", "-1", "9223372036854775808", "1.5"})
    void postHeaderInvalidoEs400(String header) throws Exception {
        error(mvc.perform(post("/api/reclamos").header("X-Usuario-Id", header)
                .contentType(MediaType.APPLICATION_JSON).content(creacion(datos.categoria()))),
                400, "DATOS_INVALIDOS", "/api/reclamos");
    }

    @Test
    void listadosPorRolConservanOrdenYSoloIncluyenCamposDelResumen() throws Exception {
        listado(datos.ciudadano(), datos.ingresado(), datos.asignado());
        listado(datos.agente(), datos.resuelto(), datos.asignado());
        listado(datos.administrador(), datos.resuelto(), datos.ingresado(), datos.ajeno(), datos.asignado());
        JsonNode resumen = leer(mvc.perform(get("/api/reclamos").header("X-Usuario-Id", datos.ciudadano()))
                .andExpect(status().isOk()).andReturn()).get(0);
        campos(resumen, "numero", "direccion", "estado", "prioridad", "vencido", "fechaCreacion", "fechaLimite",
                "categoria", "barrio", "ciudadano", "area");
    }

    @Test
    void filtrosCombinadosEIdsInexistentesRespetanContrato() throws Exception {
        mvc.perform(get("/api/reclamos").header("X-Usuario-Id", datos.administrador())
                        .param("areaId", datos.area().toString()).param("ciudadanoId", datos.ciudadano().toString())
                        .param("estado", "ASIGNADO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].numero").value(contains(datos.asignado())));
        mvc.perform(get("/api/reclamos").header("X-Usuario-Id", datos.ciudadano()).param("estado", "INGRESADO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].numero").value(contains(datos.ingresado())));
        for (String filtro : List.of("areaId", "ciudadanoId")) {
            mvc.perform(get("/api/reclamos").header("X-Usuario-Id", datos.administrador())
                            .param(filtro, Long.toString(Long.MAX_VALUE)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
    }

    @Test
    void filtrosAdministrativosSon403ParaCiudadanoYAgente() throws Exception {
        for (Long usuario : List.of(datos.ciudadano(), datos.agente())) {
            for (String filtro : List.of("areaId", "ciudadanoId")) {
                error(mvc.perform(get("/api/reclamos").header("X-Usuario-Id", usuario).param(filtro, "1")),
                        403, "ACCESO_DENEGADO", "/api/reclamos");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"estado,NO_EXISTE", "estado,1", "areaId,texto", "areaId,0", "ciudadanoId,-1", "ciudadanoId,1.5"})
    void queryMalFormadaEs400(String campo, String valor) throws Exception {
        error(mvc.perform(get("/api/reclamos").header("X-Usuario-Id", datos.administrador()).param(campo, valor)),
                400, "DATOS_INVALIDOS", "/api/reclamos");
    }

    @Test
    void consultaIndividualDevuelveAccionesDelUsuarioYNoSerializaRelacionesInversas() throws Exception {
        String ruta = "/api/reclamos/" + datos.asignado();
        mvc.perform(get(ruta).header("X-Usuario-Id", datos.ciudadano())).andExpect(status().isOk())
                .andExpect(jsonPath("$.accionesDisponibles").value(contains("CANCELADO")))
                .andExpect(jsonPath("$.historial[0].reclamo").doesNotExist())
                .andExpect(jsonPath("$.ciudadano.dni").doesNotExist());
        mvc.perform(get(ruta).header("X-Usuario-Id", datos.agente())).andExpect(status().isOk())
                .andExpect(jsonPath("$.accionesDisponibles").value(contains("EN_PROCESO")));
        mvc.perform(get(ruta).header("X-Usuario-Id", datos.administrador())).andExpect(status().isOk())
                .andExpect(jsonPath("$.accionesDisponibles").value(contains("ASIGNADO")));
    }

    @Test
    void consultaIndividualInexistenteEs404YSinPermisoEs403() throws Exception {
        error(mvc.perform(get("/api/reclamos/REC-INEXISTENTE").header("X-Usuario-Id", datos.ciudadano())),
                404, "RECURSO_NO_ENCONTRADO", "/api/reclamos/REC-INEXISTENTE");
        String ruta = "/api/reclamos/" + datos.ajeno();
        error(mvc.perform(get(ruta).header("X-Usuario-Id", datos.ciudadano())), 403, "ACCESO_DENEGADO", ruta);
    }

    @Test
    void patchEstadoValidoPersisteYMapeaAgenteHistorialYAcciones() throws Exception {
        String ruta = "/api/reclamos/" + datos.asignado() + "/estado";
        mvc.perform(patch(ruta).header("X-Usuario-Id", datos.agente()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PROCESO\",\"observacion\":\"Cuadrilla\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.agente.id").value(datos.agente()))
                .andExpect(jsonPath("$.accionesDisponibles").value(contains("RESUELTO")))
                .andExpect(jsonPath("$.historial", hasSize(3)));
        assertThat(reclamos.buscarPorNumero(datos.asignado()).orElseThrow().getEstado()).isEqualTo(EstadoReclamo.EN_PROCESO);
    }

    @Test
    void patchTransicionInvalidaPrevaleceSobrePermisosYEs409() throws Exception {
        String ruta = "/api/reclamos/" + datos.ingresado() + "/estado";
        error(mvc.perform(patch(ruta).header("X-Usuario-Id", datos.otroCiudadano()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RESUELTO\"}")), 409, "REGLA_NEGOCIO", ruta);
    }

    @Test
    void patchEstadoSinPermisoEs403() throws Exception {
        String ruta = "/api/reclamos/" + datos.asignado() + "/estado";
        error(mvc.perform(patch(ruta).header("X-Usuario-Id", datos.otroAgente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_PROCESO\"}")), 403, "ACCESO_DENEGADO", ruta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"NO_EXISTE\"", "1", "null", "true", "\"ASIGNADO\""})
    void patchEstadoInvalidoEs400(String estado) throws Exception {
        String ruta = "/api/reclamos/" + datos.asignado() + "/estado";
        error(mvc.perform(patch(ruta).header("X-Usuario-Id", datos.administrador()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":" + estado + "}")), 400, "DATOS_INVALIDOS", ruta);
    }

    @Test
    void observacionExcesivaEs400EnAmbosPatch() throws Exception {
        String base = "/api/reclamos/" + datos.asignado();
        error(mvc.perform(patch(base + "/estado").header("X-Usuario-Id", datos.agente()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("estado", "EN_PROCESO", "observacion", "x".repeat(1001))))),
                400, "DATOS_INVALIDOS", base + "/estado");
        error(mvc.perform(patch(base + "/asignacion").header("X-Usuario-Id", datos.administrador()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("areaId", datos.otraArea(), "observacion", "x".repeat(1001))))),
                400, "DATOS_INVALIDOS", base + "/asignacion");
    }

    @Test
    void patchAsignacionValidaEs200() throws Exception {
        mvc.perform(patch("/api/reclamos/" + datos.ingresado() + "/asignacion").header("X-Usuario-Id", datos.administrador())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("areaId", datos.area()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.area.id").value(datos.area()))
                .andExpect(jsonPath("$.estado").value("ASIGNADO"))
                .andExpect(jsonPath("$.agente").value(nullValue()))
                .andExpect(jsonPath("$.accionesDisponibles").value(contains("ASIGNADO")));
    }

    @Test
    void patchAreaInexistenteEs404YUsuarioSinPermisoEs403() throws Exception {
        String ruta = "/api/reclamos/" + datos.ingresado() + "/asignacion";
        error(mvc.perform(patch(ruta).header("X-Usuario-Id", datos.administrador()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("areaId", Long.MAX_VALUE)))), 404, "RECURSO_NO_ENCONTRADO", ruta);
        error(mvc.perform(patch(ruta).header("X-Usuario-Id", datos.ciudadano()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("areaId", datos.area())))), 403, "ACCESO_DENEGADO", ruta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "1.5", "\"texto\""})
    void patchAreaInvalidaEs400(String area) throws Exception {
        String ruta = "/api/reclamos/" + datos.ingresado() + "/asignacion";
        error(mvc.perform(patch(ruta).header("X-Usuario-Id", datos.administrador()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaId\":" + area + "}")), 400, "DATOS_INVALIDOS", ruta);
    }

    @Test
    void notificacionesTienenDtoCorrectoYSinPermisoEs403() throws Exception {
        facade.tomar(datos.agente(), datos.asignado(), "Cuadrilla");
        String ruta = "/api/reclamos/" + datos.asignado() + "/notificaciones";
        MvcResult resultado = mvc.perform(get(ruta).header("X-Usuario-Id", datos.ciudadano()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].canal").value("INTERNO"))
                .andExpect(jsonPath("$[0].destinatario.id").value(datos.ciudadano())).andReturn();
        campos(leer(resultado).get(0), "canal", "destinatario", "mensaje", "fechaEnvio");
        error(mvc.perform(get(ruta).header("X-Usuario-Id", datos.otroCiudadano())), 403, "ACCESO_DENEGADO", ruta);
    }

    @Test
    void bandejaDeAvisosDevuelveSoloLosDelUsuarioYExigeIdentidad() throws Exception {
        facade.tomar(datos.agente(), datos.asignado(), "Cuadrilla");
        String ruta = "/api/notificaciones";
        MvcResult resultado = mvc.perform(get(ruta).header("X-Usuario-Id", datos.ciudadano()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].numeroReclamo").value(datos.asignado()))
                .andExpect(jsonPath("$[0].canal").value("INTERNO")).andReturn();
        campos(leer(resultado).get(0), "numeroReclamo", "canal", "mensaje", "fechaEnvio");
        mvc.perform(get(ruta).header("X-Usuario-Id", datos.otroCiudadano()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        error(mvc.perform(get(ruta)), 400, "DATOS_INVALIDOS", ruta);
        error(mvc.perform(get(ruta).header("X-Usuario-Id", 999999)), 404, "RECURSO_NO_ENCONTRADO", ruta);
    }

    @Test
    void usuariosYCatalogosNoExigenIdentidadYSoloExponenCamposDelContrato() throws Exception {
        JsonNode usuariosDto = leer(mvc.perform(get("/api/usuarios")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5))).andReturn());
        campos(usuariosDto.get(0), "id", "nombreCompleto", "rol", "area");
        JsonNode agente = leer(mvc.perform(get("/api/usuarios/" + datos.agente())).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("AGENTE_MUNICIPAL"))
                .andExpect(jsonPath("$.area.id").value(datos.area())).andReturn());
        campos(agente.get("area"), "id", "nombre");
        mvc.perform(get("/api/usuarios/" + datos.ciudadano())).andExpect(status().isOk())
                .andExpect(jsonPath("$.area").value(nullValue()));
        JsonNode categoria = leer(mvc.perform(get("/api/categorias")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))).andReturn()).get(0);
        campos(categoria, "id", "nombre", "descripcion", "slaHoras", "prioridadBase");
        JsonNode barrio = leer(mvc.perform(get("/api/barrios")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].municipio").value("Quilmes")).andReturn()).get(0);
        campos(barrio, "id", "nombre", "municipio");
        JsonNode area = leer(mvc.perform(get("/api/areas")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))).andReturn()).get(0);
        campos(area, "id", "nombre", "activa", "categorias", "barrios");
        campos(area.get("categorias").get(0), "id", "nombre");
        campos(area.get("barrios").get(0), "id", "nombre");
    }

    @Test
    void usuarioInexistenteEs404() throws Exception {
        String ruta = "/api/usuarios/" + Long.MAX_VALUE;
        error(mvc.perform(get(ruta)), 404, "RECURSO_NO_ENCONTRADO", ruta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"texto", "0", "-1", "9223372036854775808"})
    void usuarioIdMalFormadoEs400(String id) throws Exception {
        String ruta = "/api/usuarios/" + id;
        error(mvc.perform(get(ruta)), 400, "DATOS_INVALIDOS", ruta);
    }

    @Test
    void errorInesperadoEsGenericoYNoExponeDatosInternos() throws Exception {
        doThrow(new IllegalStateException("SECRETO_INTERNO_NO_EXPONER")).when(facade).usuario(Long.MAX_VALUE);
        String ruta = "/api/usuarios/" + Long.MAX_VALUE;
        MvcResult resultado = error(mvc.perform(get(ruta)), 500, "ERROR_INTERNO", ruta);
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("SECRETO_INTERNO", "stackTrace", "IllegalStateException");
    }

    @Test
    void deleteReclamosDevuelve405ConErrorResponse() throws Exception {
        MvcResult resultado = error(mvc.perform(delete("/api/reclamos").header("X-Usuario-Id", datos.ciudadano())),
                405, "METODO_NO_PERMITIDO", "/api/reclamos");
        assertThat(leer(resultado).get("mensaje").asText())
                .isEqualTo("El método HTTP utilizado no está permitido para este recurso.");
        assertThat(resultado.getResponse().getHeader("Allow")).contains("GET", "POST");
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("stackTrace", "HttpRequestMethodNotSupportedException");
    }

    @Test
    void postReclamosConTextoPlanoDevuelve415ConErrorResponse() throws Exception {
        MvcResult resultado = error(mvc.perform(post("/api/reclamos").header("X-Usuario-Id", datos.ciudadano())
                .contentType(MediaType.TEXT_PLAIN).content(creacion(datos.categoria()))),
                415, "TIPO_CONTENIDO_NO_SOPORTADO", "/api/reclamos");
        assertThat(leer(resultado).get("mensaje").asText())
                .isEqualTo("El tipo de contenido enviado no está soportado para este recurso.");
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("stackTrace", "HttpMediaTypeNotSupportedException");
    }

    private String creacion(Long categoria) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("categoriaId", categoria).put("barrioId", datos.barrio())
                .put("descripcion", "Pozo en la calle").put("direccion", "Belgrano 450");
        return json.writeValueAsString(body);
    }

    private void listado(Long usuario, String... numeros) throws Exception {
        mvc.perform(get("/api/reclamos").header("X-Usuario-Id", usuario)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].numero").value(contains(numeros)));
    }

    private MvcResult error(ResultActions resultado, int status, String codigo, String ruta) throws Exception {
        MvcResult respuesta = resultado.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.ruta").value(ruta)).andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.detalles").isArray()).andExpect(jsonPath("$.fecha").isString()).andReturn();
        JsonNode dto = leer(respuesta);
        campos(dto, "status", "codigo", "mensaje", "detalles", "ruta", "fecha");
        assertThat(LocalDateTime.parse(dto.get("fecha").asText())).isNotNull();
        if (status != 400) {
            assertThat(dto.get("detalles")).isEmpty();
        }
        return respuesta;
    }

    private JsonNode leer(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsByteArray());
    }

    private void campos(JsonNode dto, String... esperados) {
        List<String> nombres = new ArrayList<>();
        dto.fieldNames().forEachRemaining(nombres::add);
        assertThat(nombres).containsExactlyInAnyOrder(esperados);
    }

    private Reclamo crearFixture(Ciudadano ciudadano, Categoria categoria, Barrio barrio, int dia) {
        Clock reloj = Clock.fixed(Instant.parse("2026-01-0" + dia + "T12:00:00Z"), ZoneOffset.UTC);
        return new ReclamoFactory(reloj).crear(ciudadano, categoria, barrio, "Pozo", "Calle 450");
    }
}
