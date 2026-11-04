package ar.edu.uade.reclamos.aplicacion.configuracion;

import ar.edu.uade.reclamos.aplicacion.control.ControlesDeUso;
import ar.edu.uade.reclamos.aplicacion.control.LimiteDiarioDeReclamos;
import ar.edu.uade.reclamos.aplicacion.estrategia.AsignacionPorCargaDeTrabajo;
import ar.edu.uade.reclamos.aplicacion.estrategia.AsignacionPorJurisdiccion;
import ar.edu.uade.reclamos.aplicacion.estrategia.PrioridadConRespaldo;
import ar.edu.uade.reclamos.aplicacion.estrategia.PrioridadPorCategoria;
import ar.edu.uade.reclamos.aplicacion.estrategia.PrioridadPorIA;
import ar.edu.uade.reclamos.aplicacion.estrategia.PrioridadPorPalabrasClave;
import ar.edu.uade.reclamos.aplicacion.integracion.ClienteServicioPrioridad;
import ar.edu.uade.reclamos.aplicacion.integracion.CoberturaLocal;
import ar.edu.uade.reclamos.aplicacion.integracion.CoberturaSoap;
import ar.edu.uade.reclamos.aplicacion.integracion.GeocodificadorConCache;
import ar.edu.uade.reclamos.aplicacion.integracion.GeorefGeocodificador;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaAsignacion;
import ar.edu.uade.reclamos.dominio.estrategia.EstrategiaPrioridad;
import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.integracion.CatalogoCobertura;
import ar.edu.uade.reclamos.dominio.integracion.Geocodificador;
import ar.edu.uade.reclamos.dominio.modelo.PoliticaReapertura;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
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

    /** Controles antes de crear o reabrir: teléfono verificado, cuenta no bloqueada y límite diario. */
    @Bean
    public ControlesDeUso controlesDeUso(PropiedadesReclamos propiedades, ReclamoRepository reclamos) {
        return ControlesDeUso.estandar(new LimiteDiarioDeReclamos(reclamos, propiedades.uso().reclamosPorDia()));
    }

    @Bean
    public PoliticaReapertura politicaReapertura(PropiedadesReclamos propiedades) {
        return new PoliticaReapertura(propiedades.reaperturas().maximo(), propiedades.reaperturas().plazo());
    }

    /**
     * Estrategia de prioridad activa. Con {@code ia}, la estrategia que llama a REST 2 va envuelta en el
     * respaldo por categoría (Decorator): si la IA falla, el reclamo se crea igual.
     */
    @Bean
    public EstrategiaPrioridad estrategiaPrioridad(PropiedadesReclamos propiedades) {
        return switch (propiedades.prioridad().estrategia()) {
            case "categoria" -> new PrioridadPorCategoria();
            case "palabras-clave" -> new PrioridadPorPalabrasClave();
            case "ia" -> new PrioridadConRespaldo(new PrioridadPorIA(clienteIA(propiedades.prioridad().ia())),
                    new PrioridadPorCategoria());
            default -> throw new IllegalArgumentException(
                    "reclamos.prioridad.estrategia debe ser categoria, palabras-clave o ia");
        };
    }

    private static ClienteServicioPrioridad clienteIA(PropiedadesReclamos.Ia ia) {
        return ClienteServicioPrioridad.crear(ia.url(), ia.tiempoConexion(), ia.tiempoRespuesta(),
                ia.circuito().llamadas(), ia.circuito().espera());
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

    /**
     * De dónde salen las áreas candidatas: la base del backend o el servicio SOAP. {@code ServicioAsignacion}
     * recibe un {@link CatalogoCobertura} y no sabe cuál de los dos es.
     */
    @Bean
    public CatalogoCobertura catalogoCobertura(PropiedadesReclamos propiedades, AreaMunicipalRepository areas) {
        PropiedadesReclamos.Cobertura cobertura = propiedades.cobertura();
        return switch (cobertura.origen()) {
            case "local" -> new CoberturaLocal(areas);
            case "soap" -> new CoberturaSoap(CoberturaSoap.crearCliente(cobertura.url(), cobertura.tiempoLimite()),
                    areas);
            default -> throw new IllegalArgumentException("reclamos.cobertura.origen debe ser local o soap");
        };
    }

    /**
     * Geocodificador de las direcciones: Georef, envuelto en una caché (Proxy). Se piden el doble de resultados
     * de los que se muestran, porque Georef devuelve varios sin coordenadas.
     */
    @Bean
    public Geocodificador geocodificador(PropiedadesReclamos propiedades, Clock reloj) {
        PropiedadesReclamos.Geocodificacion geocodificacion = propiedades.geocodificacion();
        return switch (geocodificacion.proveedor()) {
            case "georef" -> new GeocodificadorConCache(
                    GeorefGeocodificador.crear(geocodificacion.url(), geocodificacion.tiempoConexion(),
                            geocodificacion.tiempoRespuesta(), geocodificacion.maxSugerencias() * 2),
                    geocodificacion.cache().duracion(), geocodificacion.cache().maximo(), reloj);
            default -> throw new IllegalArgumentException("reclamos.geocodificacion.proveedor debe ser georef");
        };
    }
}
