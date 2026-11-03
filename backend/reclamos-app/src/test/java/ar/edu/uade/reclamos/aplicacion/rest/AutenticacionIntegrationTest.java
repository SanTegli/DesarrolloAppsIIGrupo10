package ar.edu.uade.reclamos.aplicacion.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.dominio.modelo.Administrador;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * El login de punta a punta, como lo hace la interfaz: pide la cookie XSRF-TOKEN, la devuelve en el encabezado,
 * inicia sesión y usa la cookie de sesión.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:autenticacion;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutenticacionIntegrationTest {

    private static final String CONTRASENA = "demo1234";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder contrasenas;

    private Long ana;

    @BeforeEach
    void usuarios() {
        jdbc.update("delete from usuarios");
        Ciudadano ciudadana = new Ciudadano("30111222", "Ana", "Perez", "ana@example.org", null);
        ciudadana.definirContrasena(contrasenas.encode(CONTRASENA));
        ana = usuarios.guardar(ciudadana).getId();
        Administrador elena = new Administrador("20555666", "Elena", "Ruiz", "elena@example.org", null);
        elena.definirContrasena(contrasenas.encode(CONTRASENA));
        usuarios.guardar(elena);
        Usuario inactivo = new Ciudadano("31222333", "Bruno", "Diaz", "bruno@example.org", null);
        inactivo.definirContrasena(contrasenas.encode(CONTRASENA));
        inactivo.desactivar();
        usuarios.guardar(inactivo);
        usuarios.guardar(new Ciudadano("32333444", "Sin", "Clave", "sinclave@example.org", null));
    }

    @Test
    void conLaCookieCsrfYLaContrasenaCorrectaQuedaLaSesionYYoDevuelveAlUsuario() throws Exception {
        Cookie token = tokenCsrf();
        assertThat(token.isHttpOnly()).as("la interfaz tiene que poder leer la cookie XSRF-TOKEN").isFalse();

        MvcResult login = login("ana@example.org", CONTRASENA, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana))
                .andExpect(jsonPath("$.rol").value("CIUDADANO"))
                .andExpect(jsonPath("$.nombreCompleto").value("Ana Perez")).andReturn();
        MockHttpSession sesion = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(sesion).isNotNull();
        assertThat(login.getResponse().getContentAsString()).doesNotContain("$2a$", "contrasena", "hash");

        mvc.perform(get("/api/auth/yo").session(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana));
        mvc.perform(get("/api/reclamos").session(sesion)).andExpect(status().isOk());
    }

    @Test
    void elEmailNoDistingueMayusculasNiEspacios() throws Exception {
        login("  ANA@Example.ORG ", CONTRASENA, tokenCsrf()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana));
    }

    @Test
    void contrasenaIncorrectaEmailInexistenteCuentaInactivaOSinContrasenaDanElMismo401() throws Exception {
        Cookie token = tokenCsrf();
        for (String[] intento : new String[][] {{"ana@example.org", "otra-clave"}, {"nadie@example.org", CONTRASENA},
                {"bruno@example.org", CONTRASENA}, {"sinclave@example.org", CONTRASENA}}) {
            MvcResult resultado = login(intento[0], intento[1], token).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                    .andReturn();
            assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("El correo o la contraseña no son correctos.");
            assertThat(resultado.getRequest().getSession(false)).as(intento[0]).isNull();
        }
    }

    @Test
    void sinTokenCsrfElLoginSeRechaza() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("ana@example.org", CONTRASENA)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));
        Cookie token = tokenCsrf();
        mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", "otro-valor")
                        .contentType(MediaType.APPLICATION_JSON).content(credenciales("ana@example.org", CONTRASENA)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));
    }

    @Test
    void elLoginCambiaElIdDeLaSesionQueTraiaElNavegador() throws Exception {
        MockHttpSession previa = new MockHttpSession();
        String idPrevio = previa.getId();
        Cookie token = tokenCsrf();

        MvcResult login = mvc.perform(post("/api/auth/login").session(previa)
                        .cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(credenciales("ana@example.org", CONTRASENA)))
                .andExpect(status().isOk()).andReturn();

        assertThat(login.getRequest().getSession(false).getId()).isNotEqualTo(idPrevio);
    }

    @Test
    void cerrarSesionLaInvalidaYDespuesYoEs401() throws Exception {
        Cookie token = tokenCsrf();
        MockHttpSession sesion = (MockHttpSession) login("ana@example.org", CONTRASENA, token)
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mvc.perform(post("/api/auth/logout").session(sesion).cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isNoContent());

        assertThat(sesion.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/yo").session(sesion)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void elRegistroCreaUnCiudadanoQuePuedeEntrar() throws Exception {
        Cookie token = tokenCsrf();
        mvc.perform(post("/api/auth/registro").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro("33444555", "Laura@Example.org", "una-clave-larga")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CIUDADANO"))
                .andExpect(jsonPath("$.nombreCompleto").value("Laura Martinez"));

        Usuario laura = usuarios.buscarPorEmail("laura@example.org").orElseThrow();
        assertThat(laura.getEmail()).isEqualTo("laura@example.org");
        assertThat(laura.getHashContrasena()).startsWith("$2a$").doesNotContain("una-clave-larga");
        login("laura@example.org", "una-clave-larga", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(laura.getId()));
    }

    @Test
    void elRegistroRechazaEmailODniRepetidosYContrasenasCortas() throws Exception {
        Cookie token = tokenCsrf();
        mvc.perform(post("/api/auth/registro").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro("33444555", "ANA@example.org", "una-clave-larga")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        mvc.perform(post("/api/auth/registro").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro("30111222", "otra@example.org", "una-clave-larga")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        mvc.perform(post("/api/auth/registro").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registro("33444555", "laura@example.org", "corta")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        assertThat(usuarios.buscarPorEmail("laura@example.org")).isEmpty();
    }

    /** Lo primero que hace la interfaz: cualquier pedido deja la cookie XSRF-TOKEN. */
    private Cookie tokenCsrf() throws Exception {
        Cookie token = mvc.perform(get("/api/categorias")).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return token;
    }

    private ResultActions login(String email, String contrasena, Cookie token) throws Exception {
        return mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(credenciales(email, contrasena)));
    }

    private static String credenciales(String email, String contrasena) {
        return "{\"email\": \"" + email + "\", \"contrasena\": \"" + contrasena + "\"}";
    }

    private static String registro(String dni, String email, String contrasena) {
        return """
                {"dni": "%s", "nombre": "Laura", "apellido": "Martinez", "email": "%s", "contrasena": "%s"}"""
                .formatted(dni, email, contrasena);
    }
}
