package ar.edu.uade.reclamos.prioridad.api;

import static org.hamcrest.Matchers.hasItem;
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

/** El endpoint completo con el proveedor simulado: sin red, sin clave y sin costo. */
@SpringBootTest(properties = "prioridad.ia.proveedor=simulado")
@AutoConfigureMockMvc
class PrioridadControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void devuelveLaPrioridadElMotivoYQuienLaSugirio() throws Exception {
        evaluar("{\"categoria\": \"Luminaria rota\", \"descripcion\": \"Poste con cables expuestos\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridad").value("CRITICA"))
                .andExpect(jsonPath("$.motivo", startsWith("Simulado:")))
                .andExpect(jsonPath("$.proveedor").value("simulado"))
                .andExpect(jsonPath("$.modelo").value("reglas-locales"));
    }

    @Test
    void camposVaciosOLargosSon400ConElDetalle() throws Exception {
        evaluar("{\"categoria\": \"\", \"descripcion\": \"" + "x".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detalles", hasItem(startsWith("categoria:"))))
                .andExpect(jsonPath("$.detalles", hasItem(startsWith("descripcion:"))))
                .andExpect(jsonPath("$.ruta").value("/api/prioridades/evaluar"));
    }

    @Test
    void cuerpoQueNoEsJsonEs400() throws Exception {
        evaluar("no es json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    private ResultActions evaluar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/prioridades/evaluar").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
