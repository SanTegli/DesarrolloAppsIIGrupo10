package ar.edu.uade.reclamos.prioridad.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** La sugerencia de categoría (PR 26) con el proveedor simulado. */
@SpringBootTest(properties = "prioridad.ia.proveedor=simulado")
@AutoConfigureMockMvc
class CategoriaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void devuelveLaCategoriaElMotivoYQuienLaSugirio() throws Exception {
        sugerir("""
                {"categoriaPropuesta": "Baldosas levantadas", "descripcion": "La vereda rota frente a la escuela",
                 "categorias": ["Bache", "Vereda rota"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("Vereda rota"))
                .andExpect(jsonPath("$.motivo", startsWith("Simulado:")))
                .andExpect(jsonPath("$.proveedor").value("simulado"));
    }

    @Test
    void siNingunaCorrespondeLaCategoriaEsNull() throws Exception {
        sugerir("""
                {"categoriaPropuesta": "Ruido", "descripcion": "Hay una fiesta muy fuerte",
                 "categorias": ["Bache", "Vereda rota"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value(nullValue()));
    }

    @Test
    void sinCategoriasOConCamposVaciosEs400() throws Exception {
        sugerir("{\"categoriaPropuesta\": \"\", \"descripcion\": \"Algo\", \"categorias\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detalles", hasItem(startsWith("categoriaPropuesta:"))))
                .andExpect(jsonPath("$.detalles", hasItem(startsWith("categorias:"))));
    }

    private ResultActions sugerir(String cuerpo) throws Exception {
        return mvc.perform(post("/api/categorias/sugerir").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
