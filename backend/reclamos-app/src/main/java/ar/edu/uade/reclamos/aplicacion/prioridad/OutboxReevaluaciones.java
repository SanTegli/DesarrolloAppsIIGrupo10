package ar.edu.uade.reclamos.aplicacion.prioridad;

import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Los pedidos de reevaluación salen por la misma <b>outbox</b> que los avisos: se guardan en
 * {@code mensajes_salientes} dentro de la transacción y {@code PublicadorMensajes} los publica después. Si
 * RabbitMQ está caído, el pedido espera en la base y no se pierde.
 *
 * <p>El primer intento va directo a la cola del consumidor ({@code ia.prioridad.reevaluar}); los siguientes, a
 * la cola de espera ({@code ia.prioridad.espera}), que los devuelve pasado el tiempo configurado.
 */
public class OutboxReevaluaciones implements SolicitudesReevaluacion {

    private final MensajeSalienteRepository mensajes;
    private final ObjectMapper json;
    private final Clock reloj;

    public OutboxReevaluaciones(MensajeSalienteRepository mensajes, ObjectMapper json, Clock reloj) {
        this.mensajes = mensajes;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    public void solicitar(String reclamoNumero) {
        guardar(reclamoNumero, 1, TopologiaReevaluacion.CLAVE);
    }

    @Override
    public void reintentar(String reclamoNumero, int intento) {
        guardar(reclamoNumero, intento, TopologiaReevaluacion.CLAVE_ESPERA);
    }

    private void guardar(String reclamoNumero, int intento, String claveRuteo) {
        String eventId = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        MensajeReevaluacion mensaje = new MensajeReevaluacion(eventId, MensajeReevaluacion.VERSION,
                MensajeReevaluacion.TIPO, reclamoNumero, intento,
                OffsetDateTime.of(ahora, reloj.getZone().getRules().getOffset(ahora)));
        mensajes.guardar(new MensajeSaliente(eventId, claveRuteo, MensajeReevaluacion.TIPO, serializar(mensaje),
                ahora));
    }

    private String serializar(MensajeReevaluacion mensaje) {
        try {
            return json.writeValueAsString(mensaje);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo armar el pedido de reevaluación", error);
        }
    }
}
