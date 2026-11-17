package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.integracion.Geocodificador;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.EstadoUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Ubicacion;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ServicioUbicacionesTest {

    private static final PropiedadesReclamos.Recuadro QUILMES =
            new PropiedadesReclamos.Recuadro(-34.82, -34.66, -58.38, -58.17);

    private final Municipio quilmes = municipio("Quilmes", 1L);
    private final Municipio berazategui = municipio("Berazategui", 2L);
    private final GeorefFalso georef = new GeorefFalso();

    private ServicioUbicaciones servicio = servicio(3, true);

    @Test
    void buscaEnCadaMunicipioYDevuelveLasSugerenciasConSuMunicipio() {
        georef.respuestas.put("Quilmes", List.of(
                new SugerenciaUbicacion("GRL BELGRANO 450, Quilmes Oeste", null, null),
                new SugerenciaUbicacion("GRL BELGRANO 450, Bernal Oeste", -34.736, -58.323),
                new SugerenciaUbicacion("GRL BELGRANO 450, Bernal Oeste", -34.736, -58.323),
                new SugerenciaUbicacion("BELGRANO 450, Córdoba", -31.41, -64.18)));
        georef.respuestas.put("Berazategui", List.of(
                new SugerenciaUbicacion("BELGRANO 450, Berazategui", -34.765, -58.212)));

        SugerenciasUbicacion resultado = servicio.sugerir(" Belgrano 450 ");

        assertThat(resultado.disponible()).isTrue();
        assertThat(resultado.sugerencias()).extracting(SugerenciasUbicacion.Opcion::nomenclatura)
                .containsExactly("GRL BELGRANO 450, Bernal Oeste", "BELGRANO 450, Berazategui");
        assertThat(resultado.sugerencias()).extracting(opcion -> opcion.municipio().getNombre())
                .containsExactly("Quilmes", "Berazategui");
        assertThat(georef.consultas).containsExactlyInAnyOrder("Belgrano 450|Quilmes", "Belgrano 450|Berazategui");
    }

    @Test
    void noDevuelveMasQueElMaximoConfigurado() {
        georef.respuestas.put("Quilmes", List.of(sugerencia("BELGRANO 100"), sugerencia("BELGRANO 200")));
        georef.respuestas.put("Berazategui", List.of(sugerencia("BELGRANO 300"), sugerencia("BELGRANO 400")));

        assertThat(servicio.sugerir("Belgrano").sugerencias()).hasSize(3);
    }

    @Test
    void sinGeorefEnNingunMunicipioRespondeNoDisponibleEnLugarDeFallar() {
        georef.caidos.addAll(Set.of("Quilmes", "Berazategui"));

        SugerenciasUbicacion resultado = servicio.sugerir("Belgrano 450");

        assertThat(resultado.disponible()).isFalse();
        assertThat(resultado.sugerencias()).isEmpty();
    }

    @Test
    void siFallaUnSoloMunicipioDevuelveLoQueEncontroEnLosOtros() {
        georef.caidos.add("Quilmes");
        georef.respuestas.put("Berazategui", List.of(sugerencia("BELGRANO 450, Berazategui")));

        SugerenciasUbicacion resultado = servicio.sugerir("Belgrano 450");

        assertThat(resultado.disponible()).isTrue();
        assertThat(resultado.sugerencias()).extracting(SugerenciasUbicacion.Opcion::nomenclatura)
                .containsExactly("BELGRANO 450, Berazategui");
    }

    @Test
    void sinEleccionLaUbicacionQuedaPendiente() {
        assertThat(servicio.confirmar(quilmes.getId(), null)).isEqualTo(Ubicacion.pendiente());
        assertThat(georef.inversas).isZero();
    }

    @Test
    void laEleccionQueGeorefUbicaEnElMunicipioQuedaConfirmadaConElProveedor() {
        georef.municipioEn = Optional.of("Quilmes");

        Ubicacion ubicacion = servicio.confirmar(quilmes.getId(),
                new UbicacionElegida(-34.736, -58.323, "GRL BELGRANO 450, Bernal Oeste"));

        assertThat(ubicacion.getEstado()).isEqualTo(EstadoUbicacion.CONFIRMADA);
        assertThat(ubicacion.getProveedor()).isEqualTo("georef");
        assertThat(ubicacion.getDireccionNormalizada()).isEqualTo("GRL BELGRANO 450, Bernal Oeste");
        assertThat(georef.inversas).isEqualTo(1);
    }

    @Test
    void laEleccionFueraDelRecuadroSeRechazaSinPreguntarle() {
        assertThatThrownBy(() -> servicio.confirmar(quilmes.getId(),
                new UbicacionElegida(-31.41, -64.18, "BELGRANO 450, Córdoba")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("La ubicación elegida queda fuera de Quilmes.");
        assertThat(georef.inversas).isZero();
    }

    @Test
    void siGeorefLaUbicaEnOtroMunicipioSeRechaza() {
        // El recuadro de Quilmes llega hasta el borde con Avellaneda: solo Georef sabe de qué lado cae.
        georef.municipioEn = Optional.of("Avellaneda");

        assertThatThrownBy(() -> servicio.confirmar(quilmes.getId(),
                new UbicacionElegida(-34.67, -58.33, "AV MITRE 100, Wilde")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("La ubicación elegida queda en Avellaneda, no en Quilmes.");
    }

    @Test
    void siGeorefNoRespondeAlcanzaConElRecuadro() {
        georef.inversaCaida = true;

        assertThat(servicio.confirmar(quilmes.getId(), new UbicacionElegida(-34.736, -58.323, "Bernal"))
                .getEstado()).isEqualTo(EstadoUbicacion.CONFIRMADA);
    }

    @Test
    void conLaConfirmacionApagadaNoLePreguntaAGeoref() {
        servicio = servicio(3, false);
        georef.municipioEn = Optional.of("Avellaneda");

        assertThat(servicio.confirmar(quilmes.getId(), new UbicacionElegida(-34.736, -58.323, "Bernal"))
                .getEstado()).isEqualTo(EstadoUbicacion.CONFIRMADA);
        assertThat(georef.inversas).isZero();
    }

    @Test
    void municipioInexistenteEs404() {
        assertThatThrownBy(() -> servicio.confirmar(99L, new UbicacionElegida(-34.736, -58.323, "Bernal")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void sinRecuadroConfiguradoSoloSeValidanLosRangos() {
        georef.municipioEn = Optional.empty();

        assertThat(servicio.confirmar(berazategui.getId(), new UbicacionElegida(-31.41, -64.18, "Otra"))
                .getEstado()).isEqualTo(EstadoUbicacion.CONFIRMADA);
        assertThatThrownBy(() -> servicio.confirmar(berazategui.getId(), new UbicacionElegida(-95, 0, "Otra")))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void losNombresSeComparanSinMayusculasNiTildes() {
        assertThat(ServicioUbicaciones.mismoNombre("Quilmes", "QUILMES ")).isTrue();
        assertThat(ServicioUbicaciones.mismoNombre("Tres de Febrero", "Trés de Febrero")).isTrue();
        assertThat(ServicioUbicaciones.mismoNombre("Quilmes", "Berazategui")).isFalse();
    }

    @Test
    void elRecuadroSeBuscaSinDistinguirMayusculas() {
        assertThat(configuracion(5, true).recuadroDe("QUILMES")).contains(QUILMES);
        assertThat(configuracion(5, true).recuadroDe("Berazategui")).isEmpty();
    }

    private ServicioUbicaciones servicio(int maximo, boolean confirmarMunicipio) {
        return new ServicioUbicaciones(georef, new DoblesEnMemoria.Municipios(quilmes, berazategui),
                configuracion(maximo, confirmarMunicipio));
    }

    private static PropiedadesReclamos.Geocodificacion configuracion(int maximo, boolean confirmarMunicipio) {
        return new PropiedadesReclamos.Geocodificacion("georef", "https://georef.test", Duration.ofSeconds(1),
                Duration.ofSeconds(3), maximo, new PropiedadesReclamos.Cache(Duration.ofMinutes(10), 500),
                confirmarMunicipio, Map.of("Quilmes", QUILMES));
    }

    private static SugerenciaUbicacion sugerencia(String nomenclatura) {
        return new SugerenciaUbicacion(nomenclatura, -34.7, -58.3);
    }

    private static Municipio municipio(String nombre, Long id) {
        Municipio municipio = new Municipio(nombre, "Buenos Aires");
        municipio.setId(id);
        return municipio;
    }

    /** Responde por municipio lo preparado y anota cada consulta. Las de búsqueda llegan en paralelo. */
    private static final class GeorefFalso implements Geocodificador {
        final Map<String, List<SugerenciaUbicacion>> respuestas = new HashMap<>();
        final Set<String> caidos = new HashSet<>();
        final List<String> consultas = new CopyOnWriteArrayList<>();
        Optional<String> municipioEn = Optional.of("Quilmes");
        boolean inversaCaida;
        int inversas;

        @Override
        public List<SugerenciaUbicacion> sugerir(String direccion, Municipio municipio) {
            consultas.add(direccion + "|" + municipio.getNombre());
            if (caidos.contains(municipio.getNombre())) {
                throw new ServicioExternoNoDisponibleException("Georef no respondió");
            }
            return respuestas.getOrDefault(municipio.getNombre(), List.of());
        }

        @Override
        public Optional<String> municipioEn(double latitud, double longitud) {
            inversas++;
            if (inversaCaida) {
                throw new ServicioExternoNoDisponibleException("Georef no respondió");
            }
            return municipioEn;
        }
    }
}
