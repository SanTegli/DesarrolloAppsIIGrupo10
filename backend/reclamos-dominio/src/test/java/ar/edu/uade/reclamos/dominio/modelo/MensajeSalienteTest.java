package ar.edu.uade.reclamos.dominio.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MensajeSalienteTest {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 2, 18, 0);

    @Test
    void naceComoPendienteSinIntentos() {
        MensajeSaliente mensaje = nuevo();

        assertThat(mensaje.isPendiente()).isTrue();
        assertThat(mensaje.getEstado()).isEqualTo(EstadoMensaje.PENDIENTE);
        assertThat(mensaje.getIntentos()).isZero();
        assertThat(mensaje.getFechaPublicacion()).isNull();
    }

    @Test
    void cadaFallaSumaUnIntentoYGuardaElErrorRecortado() {
        MensajeSaliente mensaje = nuevo();
        mensaje.registrarFalla("Connection refused");
        mensaje.registrarFalla("x".repeat(500));

        assertThat(mensaje.getIntentos()).isEqualTo(2);
        assertThat(mensaje.getUltimoError()).hasSize(MensajeSaliente.LONGITUD_MAXIMA_ERROR);
        assertThat(mensaje.isPendiente()).isTrue();
    }

    @Test
    void publicadoLimpiaElError() {
        MensajeSaliente mensaje = nuevo();
        mensaje.registrarFalla(null);
        mensaje.marcarPublicado(AHORA.plusSeconds(2));

        assertThat(mensaje.isPendiente()).isFalse();
        assertThat(mensaje.getFechaPublicacion()).isEqualTo(AHORA.plusSeconds(2));
        assertThat(mensaje.getUltimoError()).isNull();
        assertThat(mensaje.getIntentos()).isEqualTo(1);
    }

    @Test
    void validaLosDatosObligatorios() {
        assertThatThrownBy(() -> new MensajeSaliente(" ", "aviso.x", "Tipo", "{}", AHORA))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> new MensajeSaliente("e", "aviso.x", "Tipo", "x".repeat(4001), AHORA))
                .isInstanceOf(DatosInvalidosException.class);
    }

    private static MensajeSaliente nuevo() {
        return new MensajeSaliente("6f1c2a9e-4b7d-4e1f-9a52-3c8d0e7b1a44", "aviso.reclamo-creado", "ReclamoCreado",
                "{}", AHORA);
    }
}
