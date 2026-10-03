package ar.edu.uade.reclamos.aplicacion.rest.configuracion;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionJsonRest {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer entradasEstrictas() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
