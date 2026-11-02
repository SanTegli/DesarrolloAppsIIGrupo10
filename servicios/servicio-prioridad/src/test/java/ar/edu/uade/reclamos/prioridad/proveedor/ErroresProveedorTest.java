package ar.edu.uade.reclamos.prioridad.proveedor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParseException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/** Qué falla del servicio corresponde a cada error del cliente HTTP, mirando la causa y no solo el tipo. */
class ErroresProveedorTest {

    @Test
    void tiempoAgotadoAlLeerLaRespuestaEsTiempoAgotadoAunqueSpringNoLoEnvuelvaComoAcceso() {
        // Así lo entrega RestClient con HttpURLConnection: el corte salta al leer los encabezados de la respuesta.
        RestClientException alLeer = new RestClientException("Error while extracting response",
                new SocketTimeoutException("Read timed out"));

        assertThat(ErroresProveedor.traducir("Gemini", alLeer)).isInstanceOf(TiempoAgotadoException.class);
    }

    @Test
    void conexionRechazadaEsProveedorNoDisponible() {
        ResourceAccessException rechazada = new ResourceAccessException("I/O error",
                new ConnectException("Connection refused"));

        assertThat(ErroresProveedor.traducir("Gemini", rechazada)).isInstanceOf(ProveedorNoDisponibleException.class);
    }

    @Test
    void jsonIlegibleEsRespuestaInvalidaAunqueJacksonLanceUnaIOException() {
        RestClientException ilegible = new RestClientException("Error while extracting response",
                new JsonParseException(null, "Unexpected character"));

        assertThat(ErroresProveedor.traducir("Gemini", ilegible)).isInstanceOf(RespuestaInvalidaException.class);
    }
}
