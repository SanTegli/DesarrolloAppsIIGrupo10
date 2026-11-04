package ar.edu.uade.reclamos.aplicacion.control;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.comun.excepcion.LimiteExcedidoException;
import ar.edu.uade.reclamos.comun.excepcion.UsoRestringidoException;
import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** La cadena de controles antes de crear y de reabrir (Chain of Responsibility). */
class ControlesDeUsoTest {

    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final DoblesEnMemoria.Reclamos reclamos = new DoblesEnMemoria.Reclamos();
    private final ControlesDeUso controles = ControlesDeUso.estandar(new LimiteDiarioDeReclamos(reclamos, 2));
    private final LocalDateTime ahora = LocalDateTime.now(e.reloj);

    @Test
    void sinTelefonoVerificadoNoCreaNiReabre() {
        assertThatThrownBy(() -> controles.antesDeCrear(e.ciudadano, ahora))
                .isInstanceOf(UsoRestringidoException.class)
                .extracting("codigo").isEqualTo(UsoRestringidoException.TELEFONO_NO_VERIFICADO);
        assertThatThrownBy(() -> controles.antesDeReabrir(e.ciudadano, ahora))
                .isInstanceOf(UsoRestringidoException.class);
    }

    @Test
    void conTelefonoVerificadoYCuentaHabilitadaPasa() {
        Ciudadano ana = verificado(e.ciudadano);

        assertThatCode(() -> controles.antesDeCrear(ana, ahora)).doesNotThrowAnyException();
        assertThatCode(() -> controles.antesDeReabrir(ana, ahora)).doesNotThrowAnyException();
    }

    @Test
    void unaCuentaBloqueadaNoCreaNiReabre() {
        Ciudadano ana = verificado(e.ciudadano);
        ana.bloquear();

        assertThatThrownBy(() -> controles.antesDeCrear(ana, ahora))
                .isInstanceOf(UsoRestringidoException.class)
                .extracting("codigo").isEqualTo(UsoRestringidoException.CUENTA_BLOQUEADA);
        assertThatThrownBy(() -> controles.antesDeReabrir(ana, ahora)).isInstanceOf(UsoRestringidoException.class);
    }

    @Test
    void elLimiteDiarioCuentaSoloLosReclamosDeHoyDelMismoCiudadano() {
        Ciudadano ana = verificado(e.ciudadano);
        reclamos.guardar(e.ingresado());
        reclamos.guardar(e.ingresado());

        assertThatThrownBy(() -> controles.antesDeCrear(ana, ahora)).isInstanceOf(LimiteExcedidoException.class)
                .hasMessageContaining("2 reclamos hoy");
        // Al día siguiente vuelve a poder; reabrir no cuenta para el límite diario.
        assertThatCode(() -> controles.antesDeCrear(ana, ahora.plusDays(1))).doesNotThrowAnyException();
        assertThatCode(() -> controles.antesDeReabrir(ana, ahora)).doesNotThrowAnyException();
        assertThatCode(() -> controles.antesDeCrear(verificado(e.otroCiudadano), ahora)).doesNotThrowAnyException();
    }

    @Test
    void sinControlesNoRevisaNada() {
        assertThatCode(() -> ControlesDeUso.NINGUNO.antesDeCrear(e.ciudadano, ahora)).doesNotThrowAnyException();
    }

    private Ciudadano verificado(Ciudadano ciudadano) {
        ciudadano.verificarTelefono("+5491" + ciudadano.getDni(), ahora);
        return ciudadano;
    }
}
