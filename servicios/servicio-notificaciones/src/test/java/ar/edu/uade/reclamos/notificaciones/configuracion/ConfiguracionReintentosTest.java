package ar.edu.uade.reclamos.notificaciones.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.notificaciones.aviso.MensajeInvalidoException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.mail.MailSendException;
import org.springframework.retry.support.RetryTemplate;

/** Los reintentos del listener: tres para una falla pasajera, ninguno para un mensaje inválido. */
class ConfiguracionReintentosTest {

    private final RetryTemplate reintentos = new RetryTemplate();
    private final AtomicInteger intentos = new AtomicInteger();

    ConfiguracionReintentosTest() {
        new ConfiguracionReintentos().sinReintentosParaMensajesInvalidos(new RabbitProperties())
                .customize(RabbitRetryTemplateCustomizer.Target.LISTENER, reintentos);
    }

    @Test
    void unaFallaPasajeraSeIntentaTresVeces() {
        assertThatThrownBy(() -> ejecutar(new MailSendException("SMTP caído")))
                .isInstanceOf(MailSendException.class);
        assertThat(intentos).hasValue(3);
    }

    @Test
    void unMensajeInvalidoNoSeReintenta() {
        assertThatThrownBy(() -> ejecutar(new MensajeInvalidoException("versión 2")))
                .isInstanceOf(MensajeInvalidoException.class);
        assertThat(intentos).hasValue(1);
    }

    @Test
    void tampocoSiLlegaEnvueltoPorElContenedor() {
        RuntimeException envuelta = new ListenerExecutionFailedException("Listener falló",
                new MensajeInvalidoException("no es JSON"));

        assertThatThrownBy(() -> ejecutar(envuelta)).isSameAs(envuelta);
        assertThat(intentos).hasValue(1);
    }

    private void ejecutar(RuntimeException falla) {
        reintentos.execute(contexto -> {
            intentos.incrementAndGet();
            throw falla;
        });
    }
}
