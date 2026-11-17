package ar.edu.uade.reclamos.aplicacion.categoria;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** El consumidor de {@code reclamos.categoria}: pasa los pedidos válidos y manda los inválidos a la cola de errores. */
class ListenerSugerenciaCategoriaTest {

    private final ServicioSugerenciaCategoria servicio = mock(ServicioSugerenciaCategoria.class);
    private final ListenerSugerenciaCategoria listener =
            new ListenerSugerenciaCategoria(servicio, new ObjectMapper().findAndRegisterModules());

    @Test
    void unPedidoValidoLlegaAlServicioConSuIntento() {
        listener.recibir(mensaje("""
                {"eventId": "e1", "version": 1, "tipoEvento": "CategoriaPendiente", "reclamoNumero": "REC-0A1B2C3D",
                 "intento": 3, "fechaSolicitud": "2026-11-16T21:00:00-03:00"}"""));

        verify(servicio).sugerir("REC-0A1B2C3D", 3);
    }

    @Test
    void unPedidoIlegibleOConOtraVersionVaALaColaDeErrores() {
        for (String cuerpo : new String[] {"no es JSON",
                "{\"version\": 2, \"reclamoNumero\": \"REC-0A1B2C3D\", \"intento\": 1}",
                "{\"version\": 1, \"intento\": 1}",
                "{\"version\": 1, \"reclamoNumero\": \"REC-0A1B2C3D\", \"intento\": 0}"}) {
            assertThatThrownBy(() -> listener.recibir(mensaje(cuerpo)))
                    .as(cuerpo).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
        verify(servicio, never()).sugerir(anyString(), anyInt());
    }

    private static Message mensaje(String cuerpo) {
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
