package ar.edu.uade.reclamos.prioridad.api;

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
import org.springframework.test.web.servlet.MockMvc;

/**
 * El contrato OpenAPI de REST 2 sale del código. Este test lo controla y lo guarda en
 * {@code docs/openapi/prioridad.yaml}; si cambió, se commitea junto con el código.
 */
@SpringBootTest(properties = "prioridad.ia.proveedor=simulado")
@AutoConfigureMockMvc
class OpenApiPrioridadTest {

    private static final Path CONTRATO = Path.of("../../docs/openapi/prioridad.yaml");

    @Autowired
    private MockMvc mvc;

    @Test
    void documentaLaOperacionSusErroresYGuardaElContrato() throws Exception {
        JsonNode contrato = new ObjectMapper().readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode evaluar = contrato.path("paths").path("/api/prioridades/evaluar").path("post");

        assertThat(evaluar.path("responses").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("200", "400", "502", "503", "504");
        // PR 26: la sugerencia de categoría de "Otro problema", con los mismos errores.
        assertThat(contrato.path("paths").path("/api/categorias/sugerir").path("post").path("responses").fieldNames())
                .toIterable().containsExactlyInAnyOrder("200", "400", "502", "503", "504");
        assertThat(contrato.path("components").path("schemas").path("EvaluacionResponse").path("properties")
                .path("prioridad").path("enum").toString()).contains("BAJA", "MEDIA", "ALTA", "CRITICA");
        // Igual que el contrato escrito (docs/contratos/rest2-prioridad.yaml): las respuestas traen todos sus campos.
        JsonNode esquemas = contrato.path("components").path("schemas");
        assertThat(requeridos(esquemas.path("EvaluacionResponse")))
                .containsExactlyInAnyOrder("prioridad", "motivo", "proveedor", "modelo");
        assertThat(requeridos(esquemas.path("ErrorResponse")))
                .containsExactlyInAnyOrder("status", "codigo", "mensaje", "detalles", "ruta", "fecha");

        String yaml = mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml).contains("openapi:", "Servicio de prioridad");
        if (Files.isDirectory(CONTRATO.getParent())) {
            Files.writeString(CONTRATO, yaml, StandardCharsets.UTF_8);
        }
    }

    private static List<String> requeridos(JsonNode esquema) {
        List<String> nombres = new ArrayList<>();
        esquema.path("required").forEach(campo -> nombres.add(campo.asText()));
        return nombres;
    }
}
