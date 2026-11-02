package ar.edu.uade.reclamos.prioridad.configuracion;

import ar.edu.uade.reclamos.prioridad.evaluacion.EvaluadorPrioridad;
import ar.edu.uade.reclamos.prioridad.evaluacion.InstruccionesPrioridad;
import ar.edu.uade.reclamos.prioridad.proveedor.GeminiAdapter;
import ar.edu.uade.reclamos.prioridad.proveedor.OpenAiAdapter;
import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorIA;
import ar.edu.uade.reclamos.prioridad.proveedor.SimuladoAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Elige el adaptador del proveedor de IA según {@code prioridad.ia.proveedor} (Factory). Es el único lugar
 * que conoce las tres implementaciones: el resto del servicio recibe un {@link ProveedorIA}.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionProveedor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguracionProveedor.class);

    @Bean
    public ProveedorIA proveedorIA(PropiedadesIA propiedades, RestClient.Builder http, ObjectMapper json) {
        ProveedorIA proveedor = switch (propiedades.proveedor()) {
            case "gemini" -> new GeminiAdapter(cliente(http, propiedades.gemini(), propiedades.tiempoLimite()),
                    propiedades.gemini().clave(), propiedades.gemini().modelo(), json);
            case "openai" -> new OpenAiAdapter(cliente(http, propiedades.openai(), propiedades.tiempoLimite()),
                    propiedades.openai().clave(), propiedades.openai().modelo(), json);
            case "simulado" -> new SimuladoAdapter();
            default -> throw new IllegalArgumentException(
                    "prioridad.ia.proveedor debe ser gemini, openai o simulado");
        };
        LOGGER.info("Proveedor de IA: {} con el modelo {}", proveedor.nombre(), proveedor.modelo());
        if (proveedor instanceof GeminiAdapter && !propiedades.gemini().tieneClave()
                || proveedor instanceof OpenAiAdapter && !propiedades.openai().tieneClave()) {
            LOGGER.warn("No hay clave para {}: cada evaluación va a responder 503 hasta que se defina",
                    proveedor.nombre());
        }
        return proveedor;
    }

    @Bean
    public InstruccionesPrioridad instruccionesPrioridad(PropiedadesIA propiedades, ResourceLoader recursos)
            throws IOException {
        return new InstruccionesPrioridad(recursos.getResource(propiedades.instrucciones())
                .getContentAsString(StandardCharsets.UTF_8));
    }

    @Bean
    public EvaluadorPrioridad evaluadorPrioridad(InstruccionesPrioridad instrucciones, ProveedorIA proveedor) {
        return new EvaluadorPrioridad(instrucciones, proveedor);
    }

    private static RestClient cliente(RestClient.Builder http, PropiedadesIA.Proveedor proveedor, Duration tiempo) {
        if (proveedor.url() == null || proveedor.url().isBlank() || proveedor.modelo() == null
                || proveedor.modelo().isBlank()) {
            throw new IllegalArgumentException("prioridad.ia: falta la url o el modelo del proveedor elegido");
        }
        SimpleClientHttpRequestFactory conexion = new SimpleClientHttpRequestFactory();
        conexion.setConnectTimeout((int) tiempo.toMillis());
        conexion.setReadTimeout((int) tiempo.toMillis());
        return http.clone().baseUrl(proveedor.url()).requestFactory(conexion).build();
    }
}
