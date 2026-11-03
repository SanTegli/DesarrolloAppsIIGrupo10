package ar.edu.uade.reclamos.notificaciones.aviso;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Convierte el cuerpo del mensaje en {@link MensajeAviso} y controla el contrato. Campos que no conoce se
 * ignoran (así el productor puede sumar campos sin romper al consumidor); una versión que no conoce, no.
 */
public class LectorMensajes {

    public static final int VERSION = 1;
    private static final Pattern NUMERO_RECLAMO = Pattern.compile("REC-[0-9A-F]{8}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final ObjectReader lector;

    public LectorMensajes(ObjectMapper json) {
        // Sin ajustar la fecha a UTC: el correo muestra la hora con la zona en que ocurrió el evento.
        this.lector = json.readerFor(MensajeAviso.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    /** @throws MensajeInvalidoException si el mensaje no cumple el contrato */
    public MensajeAviso leer(String cuerpo) {
        MensajeAviso aviso;
        try {
            aviso = lector.readValue(cuerpo == null ? "" : cuerpo);
        } catch (IOException error) {
            throw new MensajeInvalidoException("El mensaje no es un JSON con la forma del contrato", error);
        }
        if (aviso == null) {
            throw new MensajeInvalidoException("El mensaje está vacío");
        }
        if (aviso.version() != VERSION) {
            throw new MensajeInvalidoException("Versión de mensaje desconocida: " + aviso.version());
        }
        requerir(esUuid(aviso.eventId()), "eventId tiene que ser un UUID");
        requerir(conTexto(aviso.tipoEvento()), "falta tipoEvento");
        requerir(aviso.reclamoNumero() != null && NUMERO_RECLAMO.matcher(aviso.reclamoNumero()).matches(),
                "reclamoNumero inválido");
        requerir(aviso.destinatario() != null && conTexto(aviso.destinatario().nombre()), "falta el destinatario");
        requerir(aviso.destinatario().email() != null && EMAIL.matcher(aviso.destinatario().email()).matches(),
                "email del destinatario inválido");
        requerir(conTexto(aviso.mensaje()) && aviso.mensaje().length() <= 500, "falta el mensaje o es muy largo");
        requerir(aviso.fechaEvento() != null, "falta fechaEvento");
        return aviso;
    }

    private static void requerir(boolean condicion, String motivo) {
        if (!condicion) {
            throw new MensajeInvalidoException("Mensaje inválido: " + motivo);
        }
    }

    private static boolean conTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    private static boolean esUuid(String texto) {
        if (texto == null) {
            return false;
        }
        try {
            return UUID.fromString(texto).toString().equalsIgnoreCase(texto);
        } catch (IllegalArgumentException noEs) {
            return false;
        }
    }
}
