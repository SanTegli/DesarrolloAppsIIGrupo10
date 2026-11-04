package ar.edu.uade.reclamos.aplicacion.foto;

import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Arma el servicio de fotos con el almacén local en {@code reclamos.fotos.directorio}. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionFotos {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguracionFotos.class);

    @Bean
    public AlmacenFotos almacenFotos(PropiedadesReclamos propiedades) {
        AlmacenFotosLocal almacen = new AlmacenFotosLocal(Path.of(propiedades.fotos().directorio()));
        LOGGER.info("Fotos de los reclamos en {}", almacen.getDirectorio());
        return almacen;
    }

    @Bean
    public ServicioFotos servicioFotos(PropiedadesReclamos propiedades, AlmacenFotos almacen) {
        PropiedadesReclamos.Fotos fotos = propiedades.fotos();
        return new ServicioFotos(new ProcesadorFotos(fotos.tamanoMaximo().toBytes(), fotos.ladoMaximo(),
                fotos.calidad()), almacen);
    }
}
