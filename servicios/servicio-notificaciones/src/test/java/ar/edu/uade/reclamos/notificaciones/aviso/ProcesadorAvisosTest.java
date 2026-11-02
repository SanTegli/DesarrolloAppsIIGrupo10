package ar.edu.uade.reclamos.notificaciones.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mail.MailSendException;

/** El consumidor idempotente, con la base real del servicio (H2 en memoria) y el correo simulado. */
class ProcesadorAvisosTest {

    private final EmbeddedDatabase base = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true).addScript("schema.sql").build();
    private final RegistroProcesados registro = new RegistroProcesados(new JdbcTemplate(base));
    private final List<MensajeAviso> enviados = new ArrayList<>();
    private boolean smtpCaido;

    private final ProcesadorAvisos procesador = new ProcesadorAvisos(
            new LectorMensajes(new ObjectMapper().findAndRegisterModules()), registro,
            aviso -> {
                if (smtpCaido) {
                    throw new MailSendException("Connection refused: localhost:1025");
                }
                enviados.add(aviso);
            },
            Clock.fixed(Instant.parse("2026-11-02T21:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void cerrarBase() {
        base.shutdown();
    }

    @Test
    void mandaElCorreoYRegistraElEventId() {
        String eventId = UUID.randomUUID().toString();

        assertThat(procesador.procesar(MensajesDePrueba.valido(eventId))).isEqualTo(ProcesadorAvisos.Resultado.ENVIADO);

        assertThat(enviados).singleElement().extracting(MensajeAviso::eventId).isEqualTo(eventId);
        assertThat(registro.yaProcesado(eventId)).isTrue();
    }

    @Test
    void unMensajeRepetidoNoSeVuelveAMandar() {
        String mensaje = MensajesDePrueba.valido();

        procesador.procesar(mensaje);
        assertThat(procesador.procesar(mensaje)).isEqualTo(ProcesadorAvisos.Resultado.REPETIDO);

        assertThat(enviados).hasSize(1);
    }

    @Test
    void siElCorreoFallaNoSeRegistraYElReintentoLoManda() {
        String eventId = UUID.randomUUID().toString();
        smtpCaido = true;

        assertThatThrownBy(() -> procesador.procesar(MensajesDePrueba.valido(eventId)))
                .isInstanceOf(MailSendException.class);
        assertThat(registro.yaProcesado(eventId)).isFalse();

        smtpCaido = false;
        assertThat(procesador.procesar(MensajesDePrueba.valido(eventId))).isEqualTo(ProcesadorAvisos.Resultado.ENVIADO);
        assertThat(enviados).hasSize(1);
    }

    @Test
    void unMensajeInvalidoNoMandaNada() {
        assertThatThrownBy(() -> procesador.procesar("{\"version\": 2}"))
                .isInstanceOf(MensajeInvalidoException.class);
        assertThat(enviados).isEmpty();
    }

    @Test
    void elRegistroNoAceptaElMismoEventIdDosVeces() {
        String eventId = UUID.randomUUID().toString();
        Instant ahora = Instant.parse("2026-11-02T21:00:00Z");

        assertThat(registro.registrar(eventId, "ReclamoCreado", ahora)).isTrue();
        assertThat(registro.registrar(eventId, "ReclamoCreado", ahora)).isFalse();
    }
}
