package ar.edu.uade.reclamos.prioridad.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorIA;
import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorNoDisponibleException;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaInvalidaException;
import ar.edu.uade.reclamos.prioridad.proveedor.TiempoAgotadoException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Cada falla del proveedor sale con su código: 502, 503 o 504, con el cuerpo de error de REST 1. */
@SpringBootTest
@AutoConfigureMockMvc
class ErroresDelProveedorTest {

    private static final String DESCRIPCION = "Descripción privada del vecino";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProveedorIA proveedor;

    @BeforeEach
    void nombre() {
        when(proveedor.nombre()).thenReturn("gemini");
        when(proveedor.modelo()).thenReturn("gemini-3.5-flash-lite");
    }

    @Test
    void respuestaInvalidaEs502() throws Exception {
        when(proveedor.sugerir(any())).thenReturn(new RespuestaIA("URGENTE", "x"));

        evaluar().andExpect(status().isBadGateway()).andExpect(jsonPath("$.codigo").value("RESPUESTA_INVALIDA"));
    }

    @Test
    void proveedorNoDisponibleEs503() throws Exception {
        when(proveedor.sugerir(any())).thenThrow(new ProveedorNoDisponibleException("Falta la clave de Gemini"));

        evaluar().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value("Falta la clave de Gemini"));
    }

    @Test
    void tiempoAgotadoEs504() throws Exception {
        when(proveedor.sugerir(any())).thenThrow(new TiempoAgotadoException("Gemini no respondió a tiempo"));

        evaluar().andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.codigo").value("TIEMPO_AGOTADO"));
    }

    @Test
    void elErrorNoDevuelveLaDescripcion() throws Exception {
        when(proveedor.sugerir(any())).thenThrow(new RespuestaInvalidaException("Gemini no devolvió JSON"));

        evaluar().andExpect(content().string(Matchers.not(Matchers.containsString(DESCRIPCION))));
    }

    private ResultActions evaluar() throws Exception {
        return mvc.perform(post("/api/prioridades/evaluar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoria\": \"Bache\", \"descripcion\": \"" + DESCRIPCION + "\"}"));
    }
}
