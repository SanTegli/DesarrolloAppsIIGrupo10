package ar.edu.uade.reclamos.aplicacion.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El contrato OpenAPI se genera del código. Este test lo verifica y lo guarda en
 * {@code docs/openapi/reclamos.yaml}: después de {@code ./mvnw test}, el archivo queda al día para commitearlo.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:openapi;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTest {
    static final Path CONTRATO = Path.of("..", "..", "docs", "openapi", "reclamos.yaml");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void elContratoTieneTodasLasOperacionesYElErrorComun() throws Exception {
        JsonNode contrato = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        List<String> rutas = new ArrayList<>();
        contrato.get("paths").fieldNames().forEachRemaining(rutas::add);
        assertThat(rutas).containsExactlyInAnyOrder("/api/reclamos", "/api/reclamos/{numero}",
                "/api/reclamos/{numero}/estado", "/api/reclamos/{numero}/asignacion",
                "/api/reclamos/{numero}/notificaciones", "/api/notificaciones", "/api/usuarios",
                "/api/usuarios/{id}", "/api/categorias", "/api/barrios", "/api/areas");
        assertThat(contrato.at("/components/schemas/ErrorResponse/properties").has("codigo")).isTrue();
        assertThat(contrato.at("/components/schemas/ReclamoResponse/properties").has("origenPrioridad")).isTrue();
        assertThat(contrato.at("/components/schemas/ReclamoResponse/properties").has("ubicacion")).isTrue();

        JsonNode cambiarEstado = contrato.at("/paths/~1api~1reclamos~1{numero}~1estado/patch");
        for (String codigo : List.of("200", "400", "403", "404", "409", "500")) {
            assertThat(cambiarEstado.get("responses").has(codigo)).as("respuesta %s", codigo).isTrue();
        }
        assertThat(cambiarEstado.at("/responses/409/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(cambiarEstado.get("parameters").findValuesAsText("name")).contains("X-Usuario-Id");
    }

    @Test
    void guardaElContratoEnYamlYPublicaSwaggerUi() throws Exception {
        String yaml = mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml).contains("openapi:", "Sistema de Reclamos Urbanos", "/api/reclamos");
        Files.createDirectories(CONTRATO.getParent());
        Files.writeString(CONTRATO, yaml, StandardCharsets.UTF_8);

        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
