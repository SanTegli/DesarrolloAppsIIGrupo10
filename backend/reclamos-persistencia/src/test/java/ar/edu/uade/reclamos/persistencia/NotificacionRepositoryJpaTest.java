package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.Test;

class NotificacionRepositoryJpaTest extends PersistenciaTestBase {
    @Test void persisteReferenciasYRespetaElOrdenDeCadaConsulta() {
        prepararDatos();
        Reclamo reclamo = reclamos.guardar(nuevoReclamo());
        notificaciones.guardar(new Notificacion(reclamo, ciudadano, CanalNotificacion.EMAIL, "Primera", AHORA));
        notificaciones.guardar(new Notificacion(reclamo, ciudadano, CanalNotificacion.INTERNO, "Segunda", AHORA));
        notificaciones.guardar(new Notificacion(reclamo, agente, CanalNotificacion.SMS, "Agente", AHORA.plusMinutes(1)));
        Reclamo otro = reclamos.guardar(nuevoReclamo());
        notificaciones.guardar(new Notificacion(otro, agente, CanalNotificacion.EMAIL, "Otro", AHORA.plusMinutes(2)));
        flushYLimpiar();

        assertThat(notificaciones.buscarPorReclamo(reclamo.getNumero())).extracting(Notificacion::getMensaje)
                .containsExactly("Primera", "Segunda", "Agente");
        assertThat(notificaciones.buscarPorDestinatario(ciudadano.getId())).extracting(Notificacion::getMensaje)
                .containsExactly("Segunda", "Primera");
        Notificacion recuperada = notificaciones.buscarPorReclamo(reclamo.getNumero()).get(0);
        assertThat(recuperada.getId()).isNotNull();
        assertThat(recuperada.getCanal()).isEqualTo(CanalNotificacion.EMAIL);
        assertThat(recuperada.getDestinatario()).isEqualTo(ciudadano);
        assertThat(recuperada.getReclamo()).isEqualTo(reclamo);
        assertThat(notificaciones.buscarPorReclamo("REC-INEXISTENTE")).isEmpty();
        assertThat(notificaciones.buscarPorDestinatario(-1L)).isEmpty();
    }
}

