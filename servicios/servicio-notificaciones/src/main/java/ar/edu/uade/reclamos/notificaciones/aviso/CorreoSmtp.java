package ar.edu.uade.reclamos.notificaciones.aviso;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Correo por SMTP con {@link JavaMailSender}. En el laboratorio el SMTP es Mailpit, que guarda los correos en
 * un buzón web de prueba y no los entrega a casillas reales.
 */
public class CorreoSmtp implements EnvioCorreo {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<String, String> ASUNTOS = Map.of(
            "ReclamoCreado", "lo recibimos",
            "ReclamoAsignado", "ya tiene un área a cargo",
            "EstadoReclamoCambiado", "cambió de estado",
            "ReclamoResuelto", "está resuelto",
            "ReclamoVencido", "venció el plazo de atención");

    private final JavaMailSender correo;
    private final String remitente;

    public CorreoSmtp(JavaMailSender correo, String remitente) {
        this.correo = correo;
        this.remitente = remitente;
    }

    @Override
    public void enviar(MensajeAviso aviso) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(remitente);
        mail.setTo(aviso.destinatario().email());
        mail.setSubject(asunto(aviso));
        mail.setText(cuerpo(aviso));
        correo.send(mail);
    }

    static String asunto(MensajeAviso aviso) {
        return "Reclamo " + aviso.reclamoNumero() + ": " + ASUNTOS.getOrDefault(aviso.tipoEvento(), "novedades");
    }

    static String cuerpo(MensajeAviso aviso) {
        return "Hola " + aviso.destinatario().nombre() + ":\n\n"
                + aviso.mensaje() + "\n\n"
                + "Reclamo: " + aviso.reclamoNumero() + "\n"
                + "Fecha: " + FECHA.format(aviso.fechaEvento()) + "\n\n"
                + "Este correo lo envía el Sistema de Reclamos Urbanos, un trabajo práctico de UADE. "
                + "No respondas a este mensaje.\n";
    }
}
