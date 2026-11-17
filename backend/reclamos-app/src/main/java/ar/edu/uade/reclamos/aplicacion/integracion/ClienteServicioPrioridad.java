package ar.edu.uade.reclamos.aplicacion.integracion;

import ar.edu.uade.reclamos.dominio.estrategia.PrioridadCalculada;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente de REST 2, el servicio de prioridad con IA: {@code POST /api/prioridades/evaluar} y, desde el PR 26,
 * {@code POST /api/categorias/sugerir}.
 *
 * <p>Solo manda la categoría y la descripción (y, para sugerir la categoría, lo que el vecino escribió como tipo
 * de problema y los nombres de las categorías): nunca el nombre, el email, el DNI, el teléfono ni la dirección
 * del vecino. Los tiempos de espera los fija quien lo crea.
 *
 * <p><b>Circuit breaker</b> (Resilience4j): si la mitad de las últimas llamadas falló, deja de llamar al
 * servicio durante un rato y falla enseguida. Así, con Gemini sin cuota o el servicio caído, cada alta no
 * espera 6 segundos para terminar usando el respaldo. Pasado ese rato prueba con una llamada y, si anda,
 * vuelve a la normalidad.
 */
public class ClienteServicioPrioridad {

    static final String RUTA = "/api/prioridades/evaluar";
    static final String RUTA_CATEGORIA = "/api/categorias/sugerir";
    private static final Logger LOGGER = LoggerFactory.getLogger(ClienteServicioPrioridad.class);

    record Pedido(String categoria, String descripcion) { }

    record Respuesta(String prioridad, String motivo, String proveedor, String modelo) { }

    record PedidoCategoria(String categoriaPropuesta, String descripcion, List<String> categorias) { }

    record RespuestaCategoria(String categoria, String motivo, String proveedor, String modelo) { }

    private final RestClient http;
    private final CircuitBreaker circuito;

    public ClienteServicioPrioridad(RestClient http, CircuitBreaker circuito) {
        this.http = http;
        this.circuito = circuito;
        circuito.getEventPublisher().onStateTransition(evento ->
                LOGGER.warn("Circuito del servicio de prioridad: {}", evento.getStateTransition()));
    }

    /**
     * @param llamadas cuántas llamadas recientes se miran para decidir si abrir el circuito
     * @param espera   cuánto queda abierto antes de volver a probar
     */
    public static ClienteServicioPrioridad crear(String url, Duration conexion, Duration respuesta, int llamadas,
                                                 Duration espera) {
        SimpleClientHttpRequestFactory tiempos = new SimpleClientHttpRequestFactory();
        tiempos.setConnectTimeout((int) conexion.toMillis());
        tiempos.setReadTimeout((int) respuesta.toMillis());
        RestClient http = RestClient.builder().baseUrl(url).requestFactory(tiempos).build();
        return new ClienteServicioPrioridad(http, circuito(llamadas, espera));
    }

    public static CircuitBreaker circuito(int llamadas, Duration espera) {
        return CircuitBreaker.of("servicio-prioridad", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(llamadas)
                .minimumNumberOfCalls(llamadas)
                .failureRateThreshold(50)
                .waitDurationInOpenState(espera)
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
    }

    /**
     * @throws ServicioExternoNoDisponibleException si el servicio no responde a tiempo, responde un error o
     *         algo inválido, o el circuito está abierto
     */
    public SugerenciaPrioridad sugerir(String categoria, String descripcion) {
        try {
            return circuito.executeSupplier(() -> llamar(categoria, descripcion));
        } catch (CallNotPermittedException abierto) {
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de prioridad falló varias veces seguidas; el circuito está abierto", abierto);
        }
    }

    /**
     * Qué categoría corresponde a un reclamo de "Otro problema".
     *
     * @param categorias los nombres entre los que se elige; la respuesta es uno de ellos o ninguno
     * @throws ServicioExternoNoDisponibleException igual que {@link #sugerir}, y también si sugiere una categoría
     *         que no estaba en la lista
     */
    public SugerenciaCategoria sugerirCategoria(String propuesta, String descripcion, List<String> categorias) {
        try {
            return circuito.executeSupplier(() -> llamarCategoria(propuesta, descripcion, categorias));
        } catch (CallNotPermittedException abierto) {
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de prioridad falló varias veces seguidas; el circuito está abierto", abierto);
        }
    }

    public CircuitBreaker.State estadoCircuito() {
        return circuito.getState();
    }

    private SugerenciaPrioridad llamar(String categoria, String descripcion) {
        Respuesta respuesta = enviar(RUTA, new Pedido(categoria, descripcion), Respuesta.class);
        if (respuesta == null || respuesta.prioridad() == null || respuesta.motivo() == null
                || respuesta.motivo().isBlank()) {
            throw new ServicioExternoNoDisponibleException("El servicio de prioridad respondió incompleto");
        }
        Prioridad prioridad = Arrays.stream(Prioridad.values())
                .filter(valor -> valor.name().equals(respuesta.prioridad()))
                .findFirst()
                .orElseThrow(() -> new ServicioExternoNoDisponibleException(
                        "El servicio de prioridad sugirió una prioridad desconocida"));
        return new SugerenciaPrioridad(prioridad, acotar(respuesta.motivo()), respuesta.proveedor(),
                respuesta.modelo());
    }

    private SugerenciaCategoria llamarCategoria(String propuesta, String descripcion, List<String> categorias) {
        RespuestaCategoria respuesta = enviar(RUTA_CATEGORIA,
                new PedidoCategoria(propuesta, descripcion, categorias), RespuestaCategoria.class);
        if (respuesta == null || respuesta.motivo() == null || respuesta.motivo().isBlank()) {
            throw new ServicioExternoNoDisponibleException("El servicio de prioridad respondió incompleto");
        }
        if (respuesta.categoria() != null && !categorias.contains(respuesta.categoria())) {
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de prioridad sugirió una categoría que no existe");
        }
        return new SugerenciaCategoria(respuesta.categoria(), acotar(respuesta.motivo()), respuesta.proveedor(),
                respuesta.modelo());
    }

    private <T> T enviar(String ruta, Object pedido, Class<T> tipo) {
        try {
            return http.post().uri(ruta)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pedido)
                    .retrieve()
                    .body(tipo);
        } catch (RestClientResponseException error) {
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de prioridad respondió HTTP " + error.getStatusCode().value(), error);
        } catch (RestClientException error) {
            // Un corte de red o de tiempo puede llegar como ResourceAccessException o, si salta al leer la
            // respuesta, como RestClientException genérica: se reconoce por la causa.
            throw new ServicioExternoNoDisponibleException(esFallaDeRed(error)
                    ? "El servicio de prioridad no respondió"
                    : "La respuesta del servicio de prioridad no se pudo leer", error);
        }
    }

    /** Corte de conexión o de tiempo. Un JSON ilegible también es una IOException (de Jackson): no cuenta. */
    private static boolean esFallaDeRed(RestClientException error) {
        if (error instanceof ResourceAccessException) {
            return true;
        }
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException || causa instanceof SocketException
                    || causa instanceof UnknownHostException || causa instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String acotar(String motivo) {
        String limpio = motivo.strip();
        int maximo = PrioridadCalculada.LONGITUD_MAXIMA_MOTIVO;
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo - 1).stripTrailing() + "…";
    }
}
