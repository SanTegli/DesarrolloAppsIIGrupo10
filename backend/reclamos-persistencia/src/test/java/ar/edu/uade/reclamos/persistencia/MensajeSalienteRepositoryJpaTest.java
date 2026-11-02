package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.dominio.modelo.EstadoMensaje;
import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class MensajeSalienteRepositoryJpaTest extends PersistenciaTestBase {
    @Autowired MensajeSalienteRepository mensajes;

    @Test
    void devuelveLosPendientesMasViejosPrimeroHastaElLimite() {
        MensajeSaliente primero = mensajes.guardar(mensaje("e1"));
        MensajeSaliente publicado = mensaje("e2");
        publicado.marcarPublicado(AHORA.plusSeconds(1));
        mensajes.guardar(publicado);
        mensajes.guardar(mensaje("e3"));
        mensajes.guardar(mensaje("e4"));
        flushYLimpiar();

        assertThat(mensajes.buscarPendientes(2)).extracting(MensajeSaliente::getEventId).containsExactly("e1", "e3");
        assertThat(mensajes.contarPorEstado(EstadoMensaje.PENDIENTE)).isEqualTo(3);
        assertThat(mensajes.contarPorEstado(EstadoMensaje.PUBLICADO)).isEqualTo(1);
        assertThat(mensajes.buscarPorEventId(primero.getEventId())).get()
                .extracting(MensajeSaliente::getContenido).isEqualTo("{\"eventId\":\"e1\"}");
    }

    @Test
    void guardaLaFallaYLaPublicacion() {
        MensajeSaliente mensaje = mensajes.guardar(mensaje("e5"));
        mensaje.registrarFalla("Connection refused");
        mensajes.guardar(mensaje);
        flushYLimpiar();

        MensajeSaliente fallido = mensajes.buscarPorEventId("e5").orElseThrow();
        assertThat(fallido.getIntentos()).isEqualTo(1);
        assertThat(fallido.getUltimoError()).isEqualTo("Connection refused");
        fallido.marcarPublicado(AHORA.plusMinutes(1));
        mensajes.guardar(fallido);
        flushYLimpiar();

        MensajeSaliente publicado = mensajes.buscarPorEventId("e5").orElseThrow();
        assertThat(publicado.getEstado()).isEqualTo(EstadoMensaje.PUBLICADO);
        assertThat(publicado.getFechaPublicacion()).isEqualTo(AHORA.plusMinutes(1));
        assertThat(publicado.getUltimoError()).isNull();
    }

    @Test
    void elEventIdEsUnico() {
        mensajes.guardar(mensaje("repetido"));
        assertThatThrownBy(() -> {
            mensajes.guardar(mensaje("repetido"));
            flushYLimpiar();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static MensajeSaliente mensaje(String eventId) {
        return new MensajeSaliente(eventId, "aviso.reclamo-creado", "ReclamoCreado",
                "{\"eventId\":\"" + eventId + "\"}", AHORA);
    }
}
