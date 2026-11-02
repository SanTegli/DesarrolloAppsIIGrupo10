package ar.edu.uade.reclamos.prioridad.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionOpenApi {

    @Bean
    public OpenAPI contratoPrioridad() {
        return new OpenAPI().info(new Info()
                .title("Sistema de Reclamos Urbanos — Servicio de prioridad (REST 2)")
                .version("1.0.0")
                .description("Sugiere la prioridad de un reclamo a partir de su categoría y su descripción con "
                        + "un modelo de lenguaje externo (Gemini u OpenAI). No recibe datos personales."));
    }
}
