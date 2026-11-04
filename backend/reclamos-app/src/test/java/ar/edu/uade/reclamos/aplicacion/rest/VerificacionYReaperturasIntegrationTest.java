package ar.edu.uade.reclamos.aplicacion.rest;

import static ar.edu.uade.reclamos.aplicacion.Fotos.alta;
import static ar.edu.uade.reclamos.aplicacion.Ciudadanos.verificado;
import static ar.edu.uade.reclamos.aplicacion.Sesiones.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Teléfono verificado (modo simulado), límites de uso, bloqueo de cuentas y reaperturas con límite, por la API.
 * El límite diario se baja a 2 para probarlo sin crear muchos reclamos.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:verificacion;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "reclamos.uso.reclamos-por-dia=2", "reclamos.telegram.modo=simulado"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacionYReaperturasIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired MunicipioRepository municipios;
    @Autowired BarrioRepository barrios;
    @Autowired CategoriaRepository categorias;
    @Autowired AreaMunicipalRepository areas;
    @Autowired UsuarioRepository usuarios;

    private record Datos(Long ana, Long bruno, Long carla, Long elena, Long categoria, Long barrio) { }

    private Datos datos;

    @BeforeEach
    void escenario() {
        datos = new TransactionTemplate(transacciones).execute(tx -> {
            for (String tabla : List.of("codigos_verificacion", "notificaciones", "historial_estados", "reclamos",
                    "area_barrio", "area_categoria", "usuarios", "areas_municipales", "barrios", "categorias",
                    "municipios")) {
                jdbc.update("delete from " + tabla);
            }
            Municipio quilmes = municipios.guardar(new Municipio("Quilmes", "Buenos Aires"));
            Barrio bernal = barrios.guardar(new Barrio("Bernal", quilmes));
            Categoria luminaria = categorias.guardar(new Categoria("Luminaria rota", "Farol", 48, Prioridad.MEDIA));
            AreaMunicipal alumbrado = new AreaMunicipal("Alumbrado", "alumbrado@example.org", quilmes);
            alumbrado.agregarBarrio(bernal);
            alumbrado.agregarCategoria(luminaria);
            alumbrado = areas.guardar(alumbrado);
            // Ana todavía no verificó su teléfono; Bruno sí.
            Usuario ana = usuarios.guardar(new Ciudadano("30111222", "Ana", "Perez", "ana@example.org", null));
            Usuario bruno = usuarios.guardar(verificado(new Ciudadano("31222333", "Bruno", "Diaz", "bruno@example.org",
                    null)));
            Usuario carla = usuarios.guardar(new AgenteMunicipal("25333444", "Carla", "Gomez", "carla@example.org",
                    null, alumbrado));
            Usuario elena = usuarios.guardar(new Administrador("20555666", "Elena", "Ruiz", "elena@example.org", null));
            return new Datos(ana.getId(), bruno.getId(), carla.getId(), elena.getId(), luminaria.getId(),
                    bernal.getId());
        });
    }

    @Test
    void sinTelefonoVerificadoNoCreaReclamosYConLaVerificacionSimuladaSi() throws Exception {
        crearReclamo(datos.ana()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("TELEFONO_NO_VERIFICADO"));

        String codigo = pedirCodigo(datos.ana()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.modo").value("simulado"))
                .andExpect(jsonPath("$.enlace").value(nullValue()))
                .andExpect(jsonPath("$.vence").exists())
                .andReturn().getResponse().getContentAsString();
        String valor = json.readTree(codigo).get("codigo").asText();

        confirmarSimulado(datos.ana(), valor, "+54 9 11 5555-0000").andExpect(status().isOk())
                .andExpect(jsonPath("$.telefonoVerificado").value(endsWith("0000")))
                .andExpect(jsonPath("$.telefonoVerificado").value(not("+5491155550000")));
        mvc.perform(get("/api/auth/yo").with(como(datos.ana()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.telefonoVerificado").value(endsWith("0000")));
        crearReclamo(datos.ana()).andExpect(status().isCreated());
    }

    @Test
    void unNumeroYaVerificadoEnOtraCuentaSeRechaza() throws Exception {
        String bruno = "+5491" + "31222333";
        String codigo = codigoDe(datos.ana());

        confirmarSimulado(datos.ana(), codigo, bruno).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        assertThat(((Ciudadano) usuarios.buscarPorId(datos.ana()).orElseThrow()).tieneTelefonoVerificado()).isFalse();
    }

    @Test
    void hastaTresCodigosPorHoraYSoloSeConfirmaElCodigoPropio() throws Exception {
        String codigo = codigoDe(datos.ana());
        codigoDe(datos.ana());
        codigoDe(datos.ana());
        pedirCodigo(datos.ana()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));

        confirmarSimulado(datos.bruno(), codigo, "+5491177770000").andExpect(status().isNotFound());
        pedirCodigo(datos.carla()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void elLimiteDiarioDeReclamosSeRespeta() throws Exception {
        crearReclamo(datos.bruno()).andExpect(status().isCreated());
        crearReclamo(datos.bruno()).andExpect(status().isCreated());

        crearReclamo(datos.bruno()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));
    }

    @Test
    void elAdministradorBloqueaUnaCuentaQueSigueConsultandoPeroNoCrea() throws Exception {
        String reclamo = numero(crearReclamo(datos.bruno()).andExpect(status().isCreated()));

        bloquear(datos.elena(), datos.bruno(), true).andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueado").value(true));
        crearReclamo(datos.bruno()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
        mvc.perform(get("/api/reclamos/" + reclamo).with(como(datos.bruno()))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/yo").with(como(datos.bruno()))).andExpect(jsonPath("$.bloqueado").value(true));

        bloquear(datos.ana(), datos.bruno(), false).andExpect(status().isForbidden());
        bloquear(datos.elena(), datos.carla(), true).andExpect(status().isConflict());
        bloquear(datos.elena(), datos.bruno(), false).andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueado").value(false));
        crearReclamo(datos.bruno()).andExpect(status().isCreated());
    }

    @Test
    void seReabreUnaSolaVezConMotivoYDespuesNoSeOfrece() throws Exception {
        String reclamo = numero(crearReclamo(datos.bruno()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ASIGNADO")));
        cambiarEstado(datos.carla(), reclamo, "EN_PROCESO", null).andExpect(status().isOk());
        cambiarEstado(datos.carla(), reclamo, "RESUELTO", "Lámpara cambiada").andExpect(status().isOk());
        acciones(datos.bruno(), reclamo).andExpect(jsonPath("$.accionesDisponibles", hasItem("EN_PROCESO")));

        cambiarEstado(datos.bruno(), reclamo, "EN_PROCESO", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        cambiarEstado(datos.bruno(), reclamo, "EN_PROCESO", "Sigue apagada").andExpect(status().isOk());
        cambiarEstado(datos.carla(), reclamo, "RESUELTO", "Ahora sí").andExpect(status().isOk());

        acciones(datos.bruno(), reclamo)
                .andExpect(jsonPath("$.accionesDisponibles", not(hasItem("EN_PROCESO"))))
                .andExpect(jsonPath("$.accionesDisponibles", hasItem("CERRADO")));
        cambiarEstado(datos.bruno(), reclamo, "EN_PROCESO", "Otra vez").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
    }

    @Test
    void unaCuentaBloqueadaNoReabre() throws Exception {
        String reclamo = numero(crearReclamo(datos.bruno()).andExpect(status().isCreated()));
        cambiarEstado(datos.carla(), reclamo, "EN_PROCESO", null).andExpect(status().isOk());
        cambiarEstado(datos.carla(), reclamo, "RESUELTO", null).andExpect(status().isOk());
        bloquear(datos.elena(), datos.bruno(), true).andExpect(status().isOk());

        cambiarEstado(datos.bruno(), reclamo, "EN_PROCESO", "Sigue apagada").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
        // Cerrarlo sí puede: el bloqueo solo frena crear y reabrir.
        cambiarEstado(datos.bruno(), reclamo, "CERRADO", null).andExpect(status().isOk());
    }

    private ResultActions crearReclamo(Long usuario) throws Exception {
        return mvc.perform(alta("""
                        {"categoriaId": %d, "barrioId": %d, "descripcion": "Farol apagado",
                         "direccion": "Belgrano 450"}""".formatted(datos.categoria(), datos.barrio())).with(como(usuario)));
    }

    private ResultActions pedirCodigo(Long usuario) throws Exception {
        return mvc.perform(post("/api/verificacion-telefono").with(como(usuario)));
    }

    private String codigoDe(Long usuario) throws Exception {
        String respuesta = pedirCodigo(usuario).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(respuesta).get("codigo").asText();
    }

    private ResultActions confirmarSimulado(Long usuario, String codigo, String telefono) throws Exception {
        return mvc.perform(post("/api/verificacion-telefono/simulada").with(como(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\": \"" + codigo + "\", \"telefono\": \"" + telefono + "\"}"));
    }

    private ResultActions bloquear(Long actor, Long usuario, boolean bloqueado) throws Exception {
        return mvc.perform(patch("/api/usuarios/" + usuario + "/bloqueo").with(como(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"bloqueado\": " + bloqueado + "}"));
    }

    private ResultActions cambiarEstado(Long usuario, String numero, String estado, String observacion)
            throws Exception {
        String cuerpo = observacion == null ? "{\"estado\": \"" + estado + "\"}"
                : "{\"estado\": \"" + estado + "\", \"observacion\": \"" + observacion + "\"}";
        return mvc.perform(patch("/api/reclamos/" + numero + "/estado").with(como(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions acciones(Long usuario, String numero) throws Exception {
        return mvc.perform(get("/api/reclamos/" + numero).with(como(usuario))).andExpect(status().isOk());
    }

    private String numero(ResultActions resultado) throws Exception {
        JsonNode reclamo = json.readTree(resultado.andReturn().getResponse().getContentAsString());
        return reclamo.get("numero").asText();
    }
}
