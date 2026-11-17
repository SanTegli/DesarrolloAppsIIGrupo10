package ar.edu.uade.reclamos.aplicacion.categoria;

import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Los pedidos de sugerencia de categoría salen por la misma <b>outbox</b> que los avisos y las reevaluaciones:
 * se guardan en {@code mensajes_salientes} dentro de la transacción del alta y {@code PublicadorMensajes} los
 * publica después.
 */
public class OutboxSugerenciasCategoria implements SolicitudesSugerenciaCategoria {

    private final MensajeSalienteRepository mensajes;
    private final ObjectMapper json;
    private final Clock reloj;

    public OutboxSugerenciasCategoria(MensajeSalienteRepository mensajes, ObjectMapper json, Clock reloj) {
        this.mensajes = mensajes;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    public void solicitar(String reclamoNumero) {
        guardar(reclamoNumero, 1, TopologiaSugerenciaCategoria.CLAVE);
    }

    @Override
    public void reintentar(String reclamoNumero, int intento) {
        guardar(reclamoNumero, intento, TopologiaSugerenciaCategoria.CLAVE_ESPERA);
    }

    private void guardar(String reclamoNumero, int intento, String claveRuteo) {
        String eventId = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        MensajeSugerenciaCategoria mensaje = new MensajeSugerenciaCategoria(eventId,
                MensajeSugerenciaCategoria.VERSION, MensajeSugerenciaCategoria.TIPO, reclamoNumero, intento,
                OffsetDateTime.of(ahora, reloj.getZone().getRules().getOffset(ahora)));
        mensajes.guardar(new MensajeSaliente(eventId, claveRuteo, MensajeSugerenciaCategoria.TIPO,
                serializar(mensaje), ahora));
    }

    private String serializar(MensajeSugerenciaCategoria mensaje) {
        try {
            return json.writeValueAsString(mensaje);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo armar el pedido de sugerencia de categoría", error);
        }
    }
}
