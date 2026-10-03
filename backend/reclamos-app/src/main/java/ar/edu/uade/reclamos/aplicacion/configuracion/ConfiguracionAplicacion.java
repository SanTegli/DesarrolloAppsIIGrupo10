package ar.edu.uade.reclamos.aplicacion.configuracion;

import ar.edu.uade.reclamos.aplicacion.estrategia.AsignacionPorCargaDeTrabajo;
import ar.edu.uade.reclamos.aplicacion.estrategia.AsignacionPorJurisdiccion;
import ar.edu.uade.reclamos.aplicacion.estrategia.PrioridadPorCategoria;
import ar.edu.uade.reclamos.aplicacion.estrategia.PrioridadPorPalabrasClave;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaAsignacion;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesReclamos.class)
public class ConfiguracionAplicacion {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public ReclamoFactory reclamoFactory(Clock reloj) {
        return new ReclamoFactory(reloj);
    }

    @Bean
    public EstrategiaPrioridad estrategiaPrioridad(PropiedadesReclamos propiedades) {
        return switch (propiedades.prioridad().estrategia()) {
            case "categoria" -> new PrioridadPorCategoria();
            case "palabras-clave" -> new PrioridadPorPalabrasClave();
            default -> throw new IllegalArgumentException(
                    "reclamos.prioridad.estrategia debe ser categoria o palabras-clave");
        };
    }

    @Bean
    public EstrategiaAsignacion estrategiaAsignacion(PropiedadesReclamos propiedades,
                                                    ReclamoRepository reclamos) {
        return switch (propiedades.asignacion().estrategia()) {
            case "jurisdiccion" -> new AsignacionPorJurisdiccion();
            case "carga-trabajo" -> new AsignacionPorCargaDeTrabajo(reclamos);
            default -> throw new IllegalArgumentException(
                    "reclamos.asignacion.estrategia debe ser jurisdiccion o carga-trabajo");
        };
    }
}
