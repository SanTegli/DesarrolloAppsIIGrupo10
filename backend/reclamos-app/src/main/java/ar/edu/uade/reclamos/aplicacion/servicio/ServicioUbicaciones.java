package ar.edu.uade.reclamos.aplicacion.servicio;

import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.integracion.Geocodificador;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Ubicacion;
import ar.edu.uade.reclamos.dominio.repositorio.MunicipioRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Ubicación del reclamo en el mapa, en dos pasos:
 *
 * <ol>
 *   <li>{@link #sugerir}: antes del alta, busca la dirección en el geocodificador, una vez por municipio y en
 *       paralelo, y devuelve las opciones con coordenadas y su municipio. La dirección puede ser ambigua (hay un
 *       "Belgrano 450" en cada municipio): el ciudadano elige, y con eso queda elegido el municipio.</li>
 *   <li>{@link #confirmar}: en el alta, valida que la opción elegida caiga en el municipio del reclamo y la
 *       convierte en {@link Ubicacion}. Sin elección, la ubicación queda pendiente.</li>
 * </ol>
 *
 * <p>Si el geocodificador no responde, el alta no se traba: el ciudadano elige el municipio de una lista y el
 * reclamo se crea con la ubicación pendiente.
 */
@Service
public class ServicioUbicaciones {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioUbicaciones.class);

    private final Geocodificador geocodificador;
    private final MunicipioRepository municipios;
    private final PropiedadesReclamos.Geocodificacion configuracion;

    @Autowired
    public ServicioUbicaciones(Geocodificador geocodificador, MunicipioRepository municipios,
                               PropiedadesReclamos propiedades) {
        this(geocodificador, municipios, propiedades.geocodificacion());
    }

    public ServicioUbicaciones(Geocodificador geocodificador, MunicipioRepository municipios,
                               PropiedadesReclamos.Geocodificacion configuracion) {
        this.geocodificador = geocodificador;
        this.municipios = municipios;
        this.configuracion = configuracion;
    }

    /**
     * Sugerencias para la dirección en todos los municipios. Nunca lanza por una falla del proveedor: si no
     * respondió para ningún municipio, devuelve {@code disponible: false}; si respondió para algunos, devuelve
     * lo que encontró en esos.
     */
    public SugerenciasUbicacion sugerir(String direccion) {
        String limpia = direccion.strip();
        List<Municipio> todos = municipios.buscarTodos();
        List<Optional<List<SugerenciaUbicacion>>> respuestas = consultarEnParalelo(limpia, todos);
        if (!todos.isEmpty() && respuestas.stream().allMatch(Optional::isEmpty)) {
            return SugerenciasUbicacion.noDisponible();
        }
        Map<String, SugerenciasUbicacion.Opcion> utiles = new LinkedHashMap<>();
        for (int i = 0; i < todos.size(); i++) {
            Municipio municipio = todos.get(i);
            respuestas.get(i).orElse(List.of()).stream()
                    .filter(SugerenciaUbicacion::tieneCoordenadas)
                    .filter(sugerencia -> dentroDelRecuadro(municipio, sugerencia.latitud(), sugerencia.longitud()))
                    .forEach(sugerencia -> utiles.putIfAbsent(sugerencia.nomenclatura(),
                            new SugerenciasUbicacion.Opcion(sugerencia.nomenclatura(), sugerencia.latitud(),
                                    sugerencia.longitud(), municipio)));
        }
        return new SugerenciasUbicacion(true,
                utiles.values().stream().limit(configuracion.maxSugerencias()).toList());
    }

    /**
     * La ubicación a guardar con el reclamo.
     *
     * @param elegida la sugerencia que eligió el ciudadano, o {@code null} si no eligió ninguna
     * @throws DatosInvalidosException si las coordenadas quedan fuera del municipio del reclamo
     */
    public Ubicacion confirmar(Long municipioId, UbicacionElegida elegida) {
        if (elegida == null) {
            return Ubicacion.pendiente();
        }
        Municipio municipio = municipios.buscarPorId(municipioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("municipio", municipioId));
        if (!dentroDelRecuadro(municipio, elegida.latitud(), elegida.longitud())) {
            throw new DatosInvalidosException("La ubicación elegida queda fuera de " + municipio.getNombre() + ".");
        }
        if (configuracion.confirmarMunicipio()) {
            confirmarConElGeocodificador(municipio, elegida);
        }
        return Ubicacion.confirmada(elegida.latitud(), elegida.longitud(), elegida.direccionNormalizada(),
                configuracion.proveedor());
    }

    /**
     * Los recuadros de municipios vecinos se pisan en el borde: el geocodificador dice en cuál cae el punto de
     * verdad. Si no responde, alcanza con el recuadro: el alta no se traba por eso.
     */
    private void confirmarConElGeocodificador(Municipio municipio, UbicacionElegida elegida) {
        Optional<String> encontrado;
        try {
            encontrado = geocodificador.municipioEn(elegida.latitud(), elegida.longitud());
        } catch (ServicioExternoNoDisponibleException error) {
            LOGGER.warn("No se pudo confirmar el municipio de la ubicación elegida: {}", error.getMessage());
            return;
        }
        if (encontrado.isPresent() && !mismoNombre(encontrado.get(), municipio.getNombre())) {
            throw new DatosInvalidosException("La ubicación elegida queda en " + encontrado.get() + ", no en "
                    + municipio.getNombre() + ".");
        }
    }

    /** Una consulta por municipio, en hilos virtuales: el tiempo total es el de la más lenta, no la suma. */
    private List<Optional<List<SugerenciaUbicacion>>> consultarEnParalelo(String direccion, List<Municipio> todos) {
        try (ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Optional<List<SugerenciaUbicacion>>>> pendientes = new ArrayList<>();
            for (Municipio municipio : todos) {
                pendientes.add(CompletableFuture.supplyAsync(() -> consultar(direccion, municipio), hilos));
            }
            return pendientes.stream().map(CompletableFuture::join).toList();
        }
    }

    private Optional<List<SugerenciaUbicacion>> consultar(String direccion, Municipio municipio) {
        try {
            return Optional.of(geocodificador.sugerir(direccion, municipio));
        } catch (ServicioExternoNoDisponibleException error) {
            LOGGER.warn("Geocodificador no disponible para {}: {}", municipio.getNombre(), error.getMessage());
            return Optional.empty();
        }
    }

    private boolean dentroDelRecuadro(Municipio municipio, double latitud, double longitud) {
        return configuracion.recuadroDe(municipio.getNombre())
                .map(recuadro -> recuadro.contiene(latitud, longitud))
                .orElse(true);
    }

    /** "Berazategui" y "BERAZATEGUI", o "Tres de Febrero" con y sin tildes, son el mismo municipio. */
    static boolean mismoNombre(String uno, String otro) {
        return normalizar(uno).equals(normalizar(otro));
    }

    private static String normalizar(String nombre) {
        return Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .strip().toLowerCase(Locale.ROOT);
    }
}
