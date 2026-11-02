package ar.edu.uade.reclamos.prioridad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Servicio REST 2: sugiere la prioridad de un reclamo con un modelo de lenguaje externo. Es el componente
 * de IA del sistema, publicado como servicio (Model as a Service): el backend no habla con Gemini ni con
 * OpenAI, habla con esta API. Swagger UI en {@code http://localhost:8082/swagger-ui.html}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ServicioPrioridadApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioPrioridadApplication.class, args);
    }
}
