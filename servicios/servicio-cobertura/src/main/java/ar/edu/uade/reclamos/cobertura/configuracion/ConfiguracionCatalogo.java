package ar.edu.uade.reclamos.cobertura.configuracion;

import ar.edu.uade.reclamos.cobertura.catalogo.CatalogoCobertura;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Carga el catálogo de cobertura. Por defecto es {@code cobertura.json} del classpath; con la propiedad
 * {@code cobertura.catalogo} se puede apuntar a otro archivo sin recompilar (por ejemplo
 * {@code file:/datos/cobertura.json}).
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionCatalogo {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguracionCatalogo.class);

    @Bean
    public CatalogoCobertura catalogoCobertura(@Value("${cobertura.catalogo}") Resource archivo,
                                               ObjectMapper mapper) throws IOException {
        CatalogoCobertura catalogo = CatalogoCobertura.leer(archivo.getInputStream(), mapper);
        LOGGER.info("Catálogo de cobertura versión {} cargado de {}: {} áreas", catalogo.getVersion(),
                archivo.getDescription(), catalogo.cantidadDeAreas());
        return catalogo;
    }
}
