package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.integracion.SugerenciaUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Barrio;
import ar.edu.uade.reclamos.dominio.modelo.EstadoUbicacion;
import ar.edu.uade.reclamos.dominio.modelo.Municipio;
import ar.edu.uade.reclamos.dominio.modelo.Ubicacion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServicioUbicacionesTest {

    private static final PropiedadesReclamos.Recuadro QUILMES =
            new PropiedadesReclamos.Recuadro(-34.82, -34.66, -58.38, -58.17);

    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final Barrio otroMunicipio = barrioSinRecuadro();
    private final List<SugerenciaUbicacion> respuesta = new ArrayList<>();
    private boolean caido;

    private final ServicioUbicaciones servicio = new ServicioUbicaciones((direccion, barrio) -> {
        if (caido) {
            throw new ServicioExternoNoDisponibleException("Georef no respondió");
        }
        return respuesta;
    }, new DoblesEnMemoria.Barrios(e.barrio, otroMunicipio), configuracion(3));

    @Test
    void devuelveSoloLasSugerenciasConCoordenadasDentroDelMunicipioSinRepetir() {
        respuesta.addAll(List.of(
                new SugerenciaUbicacion("GRL BELGRANO 450, Quilmes Oeste", null, null),
                new SugerenciaUbicacion("GRL BELGRANO 450, Bernal Oeste", -34.736, -58.323),
                new SugerenciaUbicacion("GRL BELGRANO 450, Bernal Oeste", -34.736, -58.323),
                new SugerenciaUbicacion("BELGRANO 450, Córdoba", -31.41, -64.18),
                new SugerenciaUbicacion("GRL BELGRANO 450, Quilmes", -34.758, -58.297)));

        SugerenciasUbicacion resultado = servicio.sugerir(" Belgrano 450 ", e.barrio.getId());

        assertThat(resultado.disponible()).isTrue();
        assertThat(resultado.sugerencias()).extracting(SugerenciaUbicacion::nomenclatura)
                .containsExactly("GRL BELGRANO 450, Bernal Oeste", "GRL BELGRANO 450, Quilmes");
    }

    @Test
    void noDevuelveMasQueElMaximoConfigurado() {
        for (int altura = 100; altura < 600; altura += 100) {
            respuesta.add(new SugerenciaUbicacion("BELGRANO " + altura, -34.7, -58.3));
        }

        assertThat(servicio.sugerir("Belgrano", e.barrio.getId()).sugerencias()).hasSize(3);
    }

    @Test
    void sinGeorefRespondeNoDisponibleEnLugarDeFallar() {
        caido = true;

        SugerenciasUbicacion resultado = servicio.sugerir("Belgrano 450", e.barrio.getId());

        assertThat(resultado.disponible()).isFalse();
        assertThat(resultado.sugerencias()).isEmpty();
    }

    @Test
    void barrioInexistenteEs404() {
        assertThatThrownBy(() -> servicio.sugerir("Belgrano 450", 99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void sinEleccionLaUbicacionQuedaPendiente() {
        assertThat(servicio.confirmar(e.barrio.getId(), null)).isEqualTo(Ubicacion.pendiente());
    }

    @Test
    void laEleccionDentroDelMunicipioQuedaConfirmadaConElProveedor() {
        Ubicacion ubicacion = servicio.confirmar(e.barrio.getId(),
                new UbicacionElegida(-34.736, -58.323, "GRL BELGRANO 450, Bernal Oeste"));

        assertThat(ubicacion.getEstado()).isEqualTo(EstadoUbicacion.CONFIRMADA);
        assertThat(ubicacion.getProveedor()).isEqualTo("georef");
        assertThat(ubicacion.getDireccionNormalizada()).isEqualTo("GRL BELGRANO 450, Bernal Oeste");
    }

    @Test
    void laEleccionFueraDelMunicipioSeRechaza() {
        assertThatThrownBy(() -> servicio.confirmar(e.barrio.getId(),
                new UbicacionElegida(-31.41, -64.18, "BELGRANO 450, Córdoba")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("La ubicación elegida queda fuera de Quilmes.");
    }

    @Test
    void sinRecuadroConfiguradoSoloSeValidanLosRangos() {
        assertThat(servicio.confirmar(otroMunicipio.getId(), new UbicacionElegida(-31.41, -64.18, "Otra"))
                .getEstado()).isEqualTo(EstadoUbicacion.CONFIRMADA);
        assertThatThrownBy(() -> servicio.confirmar(otroMunicipio.getId(), new UbicacionElegida(-95, 0, "Otra")))
                .isInstanceOf(DatosInvalidosException.class);
    }

    @Test
    void elRecuadroSeBuscaSinDistinguirMayusculas() {
        assertThat(configuracion(5).recuadroDe("QUILMES")).contains(QUILMES);
        assertThat(configuracion(5).recuadroDe("Berazategui")).isEmpty();
    }

    private static PropiedadesReclamos.Geocodificacion configuracion(int maximo) {
        return new PropiedadesReclamos.Geocodificacion("georef", "https://georef.test", Duration.ofSeconds(1),
                Duration.ofSeconds(3), maximo, new PropiedadesReclamos.Cache(Duration.ofMinutes(10), 500),
                Map.of("Quilmes", QUILMES));
    }

    private static Barrio barrioSinRecuadro() {
        Municipio berazategui = new Municipio("Berazategui", "Buenos Aires");
        berazategui.setId(2L);
        Barrio centro = new Barrio("Centro", berazategui);
        centro.setId(2L);
        return centro;
    }
}
