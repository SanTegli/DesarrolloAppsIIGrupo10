package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.integracion.Geocodificador;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Ubicacion;
import ar.edu.uade.reclamos.dominio.repositorio.BarrioRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Ubicación del reclamo en el mapa, en dos pasos:
 *
 * <ol>
 *   <li>{@link #sugerir}: antes del alta, busca la dirección en el geocodificador y devuelve las opciones con
 *       coordenadas dentro del municipio. La dirección puede ser ambigua; el ciudadano elige.</li>
 *   <li>{@link #confirmar}: en el alta, valida la opción elegida y la convierte en {@link Ubicacion}. Sin
 *       elección, la ubicación queda pendiente.</li>
 * </ol>
 *
 * <p>Si el geocodificador no responde, el alta no se traba: el reclamo se crea con la ubicación pendiente.
 */
@Service
public class ServicioUbicaciones {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioUbicaciones.class);

    private final Geocodificador geocodificador;
    private final BarrioRepository barrios;
    private final PropiedadesReclamos.Geocodificacion configuracion;

    @Autowired
    public ServicioUbicaciones(Geocodificador geocodificador, BarrioRepository barrios,
                               PropiedadesReclamos propiedades) {
        this(geocodificador, barrios, propiedades.geocodificacion());
    }

    public ServicioUbicaciones(Geocodificador geocodificador, BarrioRepository barrios,
                               PropiedadesReclamos.Geocodificacion configuracion) {
        this.geocodificador = geocodificador;
        this.barrios = barrios;
        this.configuracion = configuracion;
    }

    /** Sugerencias para la dirección en el municipio del barrio. Nunca lanza por una falla del proveedor. */
    public SugerenciasUbicacion sugerir(String direccion, Long barrioId) {
        Barrio barrio = barrio(barrioId);
        List<SugerenciaUbicacion> encontradas;
        try {
            encontradas = geocodificador.sugerir(direccion.strip(), barrio);
        } catch (ServicioExternoNoDisponibleException error) {
            LOGGER.warn("Geocodificador no disponible: {}", error.getMessage());
            return SugerenciasUbicacion.noDisponible();
        }
        Map<String, SugerenciaUbicacion> utiles = new LinkedHashMap<>();
        encontradas.stream()
                .filter(SugerenciaUbicacion::tieneCoordenadas)
                .filter(sugerencia -> dentroDelMunicipio(barrio.getMunicipio(), sugerencia.latitud(),
                        sugerencia.longitud()))
                .forEach(sugerencia -> utiles.putIfAbsent(sugerencia.nomenclatura(), sugerencia));
        return new SugerenciasUbicacion(true,
                utiles.values().stream().limit(configuracion.maxSugerencias()).toList());
    }

    /**
     * La ubicación a guardar con el reclamo.
     *
     * @param elegida la sugerencia que eligió el ciudadano, o {@code null} si no eligió ninguna
     * @throws DatosInvalidosException si las coordenadas quedan fuera del municipio del barrio
     */
    public Ubicacion confirmar(Long barrioId, UbicacionElegida elegida) {
        if (elegida == null) {
            return Ubicacion.pendiente();
        }
        Municipio municipio = barrio(barrioId).getMunicipio();
        if (!dentroDelMunicipio(municipio, elegida.latitud(), elegida.longitud())) {
            throw new DatosInvalidosException("La ubicación elegida queda fuera de " + municipio.getNombre() + ".");
        }
        return Ubicacion.confirmada(elegida.latitud(), elegida.longitud(), elegida.direccionNormalizada(),
                configuracion.proveedor());
    }

    private boolean dentroDelMunicipio(Municipio municipio, double latitud, double longitud) {
        return configuracion.recuadroDe(municipio.getNombre())
                .map(recuadro -> recuadro.contiene(latitud, longitud))
                .orElse(true);
    }

    private Barrio barrio(Long barrioId) {
        return barrios.buscarPorId(barrioId).orElseThrow(() -> new RecursoNoEncontradoException("barrio", barrioId));
    }
}
