package ar.edu.uade.reclamos.notificaciones.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class LectorMensajesTest {

    private static final String EVENT_ID = "6f1c2a9e-4b7d-4e1f-9a52-3c8d0e7b1a44";
    private final LectorMensajes lector = new LectorMensajes(new ObjectMapper().findAndRegisterModules());

    @Test
    void leeUnMensajeDelContratoIgnorandoCamposNuevosYConservandoLaZona() {
        MensajeAviso aviso = lector.leer(MensajesDePrueba.valido(EVENT_ID));

        assertThat(aviso.eventId()).isEqualTo(EVENT_ID);
        assertThat(aviso.tipoEvento()).isEqualTo("ReclamoAsignado");
        assertThat(aviso.destinatario().email()).isEqualTo("ana@example.org");
        assertThat(aviso.fechaEvento()).isEqualTo(OffsetDateTime.parse("2026-10-26T15:00:00-03:00"));
        assertThat(aviso.fechaEvento().getOffset().getTotalSeconds()).isEqualTo(-3 * 3600);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no es json", "[]", "null",
            "\"version\": 1", "\"eventId\": \"otro\"", "\"version\": 2", "\"email\": \"sin-arroba\"",
            "\"reclamoNumero\": \"123\"", "\"mensaje\": \"\""})
    void unMensajeQueNoCumpleElContratoEsInvalido(String cambio) {
        String cuerpo = cambio.contains(":") ? reemplazar(cambio) : cambio;

        assertThatThrownBy(() -> lector.leer(cuerpo)).isInstanceOf(MensajeInvalidoException.class);
    }

    /** Reemplaza un campo del mensaje válido; con "version": 1 solo, deja un JSON sin los demás campos. */
    private static String reemplazar(String campo) {
        if (campo.equals("\"version\": 1")) {
            return "{" + campo + "}";
        }
        String nombre = campo.substring(0, campo.indexOf(':'));
        String valido = MensajesDePrueba.valido(EVENT_ID);
        return valido.replaceFirst(nombre + ": (\"[^\"]*\"|\\d+)", java.util.regex.Matcher.quoteReplacement(campo));
    }
}
