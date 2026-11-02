package ar.edu.uade.reclamos.notificaciones.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class CorreoSmtpTest {

    private final JavaMailSender smtp = mock(JavaMailSender.class);
    private final CorreoSmtp correo = new CorreoSmtp(smtp, "Reclamos <reclamos@quilmes.example>");
    private final MensajeAviso aviso = new LectorMensajes(new ObjectMapper().findAndRegisterModules())
            .leer(MensajesDePrueba.valido("6f1c2a9e-4b7d-4e1f-9a52-3c8d0e7b1a44"));

    @Test
    void armaElCorreoParaElDestinatarioConElReclamoYLaHoraDelEvento() {
        correo.enviar(aviso);

        ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(enviado.capture());
        SimpleMailMessage mail = enviado.getValue();
        assertThat(mail.getTo()).containsExactly("ana@example.org");
        assertThat(mail.getFrom()).isEqualTo("Reclamos <reclamos@quilmes.example>");
        assertThat(mail.getSubject()).isEqualTo("Reclamo REC-4F2A91BC: ya tiene un área a cargo");
        assertThat(mail.getText()).startsWith("Hola Ana Pérez:")
                .contains("Tu reclamo REC-4F2A91BC fue asignado al area Alumbrado.")
                .contains("Fecha: 26/10/2026 15:00");
    }
}
