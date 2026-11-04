package ar.edu.uade.reclamos.aplicacion.verificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.aplicacion.DoblesEnMemoria;
import ar.edu.uade.reclamos.aplicacion.EscenarioAplicacion;
import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import ar.edu.uade.reclamos.comun.excepcion.AccesoDenegadoException;
import ar.edu.uade.reclamos.comun.excepcion.LimiteExcedidoException;
import ar.edu.uade.reclamos.comun.excepcion.RecursoNoEncontradoException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.comun.excepcion.UsoRestringidoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Un número, una cuenta: códigos de un solo uso, vencimiento, límite por hora y número repetido. */
class ServicioVerificacionTelefonoTest {

    private final EscenarioAplicacion e = new EscenarioAplicacion();
    private final DoblesEnMemoria.Usuarios usuarios =
            new DoblesEnMemoria.Usuarios(e.ciudadano, e.otroCiudadano, e.agente, e.administrador);
    private final DoblesEnMemoria.Codigos codigos = new DoblesEnMemoria.Codigos();
    private final PropiedadesReclamos.Uso uso = new PropiedadesReclamos.Uso(5, 3, Duration.ofMinutes(10));
    private final RelojMovil reloj = new RelojMovil(e.reloj.instant());

    private ServicioVerificacionTelefono servicio(CanalVerificacion canal) {
        return new ServicioVerificacionTelefono(usuarios, codigos, canal, uso, reloj);
    }

    @Test
    void conTelegramDevuelveElEnlaceDelBotConElCodigo() {
        CodigoEmitido emitido = servicio(new CanalTelegram("@ReclamosQuilmesBot")).iniciar(e.ciudadano.getId());

        assertThat(emitido.codigo()).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(emitido.enlace()).isEqualTo("https://t.me/ReclamosQuilmesBot?start=" + emitido.codigo());
        assertThat(emitido.modo()).isEqualTo("telegram");
        assertThat(emitido.vence()).isEqualTo(java.time.LocalDateTime.now(reloj).plusMinutes(10));
    }

    @Test
    void confirmarGuardaElTelefonoNormalizadoYElCodigoNoSirveDosVeces() {
        ServicioVerificacionTelefono servicio = servicio(new CanalTelegram("bot"));
        String codigo = servicio.iniciar(e.ciudadano.getId()).codigo();

        assertThat(servicio.confirmar(codigo.toLowerCase(), "5491155550000")).isEqualTo(ResultadoVerificacion.VERIFICADO);
        assertThat(e.ciudadano.getTelefonoVerificado()).isEqualTo("+5491155550000");
        assertThat(servicio.confirmar(codigo, "5491155550000")).isEqualTo(ResultadoVerificacion.CODIGO_VENCIDO);
        assertThat(servicio.confirmar("NOEXISTE", "5491155550000")).isEqualTo(ResultadoVerificacion.CODIGO_INEXISTENTE);
    }

    @Test
    void unNumeroVerificadoEnOtraCuentaNoSeAcepta() {
        e.otroCiudadano.verificarTelefono("+5491155550000", java.time.LocalDateTime.now(reloj));
        ServicioVerificacionTelefono servicio = servicio(new CanalTelegram("bot"));
        String codigo = servicio.iniciar(e.ciudadano.getId()).codigo();

        assertThat(servicio.confirmar(codigo, "+54 9 11 5555-0000"))
                .isEqualTo(ResultadoVerificacion.TELEFONO_EN_OTRA_CUENTA);
        assertThat(e.ciudadano.tieneTelefonoVerificado()).isFalse();
    }

    @Test
    void cambiarDeNumeroLiberaElAnterior() {
        ServicioVerificacionTelefono servicio = servicio(new CanalTelegram("bot"));
        servicio.confirmar(servicio.iniciar(e.ciudadano.getId()).codigo(), "+5491155550000");
        servicio.confirmar(servicio.iniciar(e.ciudadano.getId()).codigo(), "+5491166660000");

        String codigoDeOtro = servicio.iniciar(e.otroCiudadano.getId()).codigo();
        assertThat(servicio.confirmar(codigoDeOtro, "+5491155550000")).isEqualTo(ResultadoVerificacion.VERIFICADO);
    }

    @Test
    void elCodigoVenceALosDiezMinutos() {
        ServicioVerificacionTelefono servicio = servicio(new CanalTelegram("bot"));
        String codigo = servicio.iniciar(e.ciudadano.getId()).codigo();
        reloj.avanzar(Duration.ofMinutes(11));

        assertThat(servicio.confirmar(codigo, "+5491155550000")).isEqualTo(ResultadoVerificacion.CODIGO_VENCIDO);
    }

    @Test
    void hastaTresCodigosPorHora() {
        ServicioVerificacionTelefono servicio = servicio(new CanalSimulado());
        for (int i = 0; i < 3; i++) {
            servicio.iniciar(e.ciudadano.getId());
        }

        assertThatThrownBy(() -> servicio.iniciar(e.ciudadano.getId())).isInstanceOf(LimiteExcedidoException.class);
        reloj.avanzar(Duration.ofMinutes(61));
        assertThat(servicio.iniciar(e.ciudadano.getId()).codigo()).hasSize(8);
    }

    @Test
    void soloCiudadanosHabilitadosPidenCodigo() {
        ServicioVerificacionTelefono servicio = servicio(new CanalSimulado());
        e.ciudadano.bloquear();

        assertThatThrownBy(() -> servicio.iniciar(e.ciudadano.getId())).isInstanceOf(UsoRestringidoException.class);
        assertThatThrownBy(() -> servicio.iniciar(e.agente.getId())).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void elModoSimuladoSoloConfirmaElCodigoPropio() {
        ServicioVerificacionTelefono simulado = servicio(new CanalSimulado());
        CodigoEmitido emitido = simulado.iniciar(e.ciudadano.getId());
        assertThat(emitido.enlace()).isNull();
        assertThat(emitido.modo()).isEqualTo("simulado");

        assertThatThrownBy(() -> simulado.confirmarSimulado(e.otroCiudadano.getId(), emitido.codigo(), "+5491155550000"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(simulado.confirmarSimulado(e.ciudadano.getId(), emitido.codigo(), "+5491155550000"))
                .isSameAs(e.ciudadano);
        assertThatThrownBy(() -> simulado.confirmarSimulado(e.ciudadano.getId(), emitido.codigo(), "+5491155550000"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void conTelegramActivoNoSePuedeConfirmarAMano() {
        ServicioVerificacionTelefono conBot = servicio(new CanalTelegram("bot"));
        String codigo = conBot.iniciar(e.ciudadano.getId()).codigo();

        assertThatThrownBy(() -> conBot.confirmarSimulado(e.ciudadano.getId(), codigo, "+5491155550000"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(e.ciudadano.tieneTelefonoVerificado()).isFalse();
    }

    /** Reloj que el test puede adelantar. */
    private static final class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
