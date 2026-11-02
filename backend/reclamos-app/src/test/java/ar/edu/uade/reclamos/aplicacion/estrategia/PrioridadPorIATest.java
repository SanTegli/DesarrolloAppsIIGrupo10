package ar.edu.uade.reclamos.aplicacion.estrategia;

import static ar.edu.uade.reclamos.aplicacion.estrategia.PrioridadTest.reclamo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.uade.reclamos.aplicacion.integracion.ClienteServicioPrioridad;
import ar.edu.uade.reclamos.dominio.estrategia.PrioridadCalculada;
import ar.edu.uade.reclamos.dominio.modelo.OrigenPrioridad;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** {@link PrioridadPorIA} y su Decorator {@link PrioridadConRespaldo}, con REST 2 simulado. */
class PrioridadPorIATest {

    private MockRestServiceServer servidor;
    private PrioridadPorIA ia;

    @BeforeEach
    void preparar() {
        RestClient.Builder http = RestClient.builder().baseUrl("http://prioridad.test");
        servidor = MockRestServiceServer.bindTo(http).build();
        ia = new PrioridadPorIA(new ClienteServicioPrioridad(http.build(),
                ClienteServicioPrioridad.circuito(10, Duration.ofMinutes(1))));
    }

    @AfterEach
    void verificar() {
        servidor.verify();
    }

    @Test
    void laIaPuedeSubirLaPrioridadYExplicaPorQue() {
        responder("CRITICA", "Cables expuestos cerca de una escuela.");

        assertThat(ia.calcular(reclamo(Prioridad.MEDIA, "Cables expuestos"))).isEqualTo(new PrioridadCalculada(
                Prioridad.CRITICA, OrigenPrioridad.IA, "Cables expuestos cerca de una escuela."));
    }

    @Test
    void laIaNoPuedeBajarLaPrioridadDeLaCategoria() {
        responder("BAJA", "Parece menor.");

        PrioridadCalculada calculada = ia.calcular(reclamo(Prioridad.ALTA, "Pozo"));

        assertThat(calculada.prioridad()).isEqualTo(Prioridad.ALTA);
        assertThat(calculada.origen()).isEqualTo(OrigenPrioridad.CATEGORIA);
        assertThat(calculada.motivo()).contains("La IA sugirió BAJA");
    }

    @Test
    void conLaMismaPrioridadQueLaCategoriaQuedaComoSugeridaPorIa() {
        responder("MEDIA", "Luminaria apagada sin otro riesgo.");

        assertThat(ia.calcular(reclamo(Prioridad.MEDIA, "Farol apagado")).origen()).isEqualTo(OrigenPrioridad.IA);
    }

    @Test
    void laConsultaUsaElNombreDeLaCategoriaYLaDescripcion() {
        servidor.expect(jsonPath("$.categoria").value("Bache"))
                .andExpect(jsonPath("$.descripcion").value("Pozo enorme"))
                .andRespond(withSuccess("{\"prioridad\": \"ALTA\", \"motivo\": \"Pozo grande.\"}",
                        MediaType.APPLICATION_JSON));

        ia.calcular(reclamo(Prioridad.BAJA, "Pozo enorme"));
    }

    @Test
    void conRespaldoSiLaIaFallaUsaLaCategoriaYLoMarca() {
        servidor.expect(anything()).andRespond(withServerError());
        PrioridadConRespaldo conRespaldo = new PrioridadConRespaldo(ia, new PrioridadPorCategoria());

        assertThat(conRespaldo.calcular(reclamo(Prioridad.MEDIA, "Pozo"))).isEqualTo(new PrioridadCalculada(
                Prioridad.MEDIA, OrigenPrioridad.RESPALDO, PrioridadConRespaldo.MOTIVO));
    }

    @Test
    void conRespaldoSiLaIaRespondeDevuelveLoMismoQueLaIa() {
        responder("ALTA", "Bache en avenida.");
        PrioridadConRespaldo conRespaldo = new PrioridadConRespaldo(ia, new PrioridadPorCategoria());

        assertThat(conRespaldo.calcular(reclamo(Prioridad.BAJA, "Bache en la avenida")).origen())
                .isEqualTo(OrigenPrioridad.IA);
    }

    private void responder(String prioridad, String motivo) {
        servidor.expect(anything()).andRespond(withSuccess(
                "{\"prioridad\": \"" + prioridad + "\", \"motivo\": \"" + motivo + "\", \"proveedor\": \"gemini\", "
                        + "\"modelo\": \"gemini-3.5-flash-lite\"}", MediaType.APPLICATION_JSON));
    }
}
