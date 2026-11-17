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

/** La comparación de duplicados (PR 27) con el proveedor simulado. */
@SpringBootTest(properties = "prioridad.ia.proveedor=simulado")
@AutoConfigureMockMvc
class DuplicadosControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void devuelveElCandidatoQueDescribeElMismoProblemaAunqueSeaDeOtraCategoria() throws Exception {
        comparar("""
                {"nuevo": {"categoria": "Luminaria rota", "descripcion": "El farol de la esquina está apagado"},
                 "candidatos": [
                   {"id": "1", "categoria": "Bache", "descripcion": "Pozo enorme en la calle"},
                   {"id": "2", "categoria": "Otro problema", "descripcion": "Farol apagado en la esquina hace días"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similar").value("2"))
                .andExpect(jsonPath("$.motivo", startsWith("Simulado:")))
                .andExpect(jsonPath("$.proveedor").value("simulado"));
    }

    @Test
    void siNingunoEsElMismoSimilarEsNull() throws Exception {
        comparar("""
                {"nuevo": {"categoria": "Residuos", "descripcion": "Bolsas de basura en la vereda"},
                 "candidatos": [{"id": "1", "categoria": "Bache", "descripcion": "Pozo enorme en la calle"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similar").value(nullValue()));
    }

    @Test
    void sinCandidatosOConIdsRepetidosEs400() throws Exception {
        comparar("{\"nuevo\": {\"categoria\": \"Bache\", \"descripcion\": \"Pozo\"}, \"candidatos\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem(startsWith("candidatos:"))));
        comparar("""
                {"nuevo": {"categoria": "Bache", "descripcion": "Pozo"},
                 "candidatos": [{"id": "1", "categoria": "Bache", "descripcion": "Pozo"},
                                {"id": "1", "categoria": "Bache", "descripcion": "Otro pozo"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    private ResultActions comparar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/duplicados/comparar").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
