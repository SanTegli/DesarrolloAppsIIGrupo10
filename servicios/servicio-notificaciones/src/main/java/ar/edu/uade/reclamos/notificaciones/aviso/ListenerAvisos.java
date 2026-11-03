package ar.edu.uade.reclamos.notificaciones.aviso;

import ar.edu.uade.reclamos.notificaciones.configuracion.TopologiaRabbit;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha la cola {@code notificaciones.email}. Si {@link ProcesadorAvisos} termina bien, Spring confirma el
 * mensaje (ACK) y RabbitMQ lo borra; si lanza una excepción, se aplican los reintentos y después la cola de
 * errores ({@code ConfiguracionReintentos}).
 */
@Component
public class ListenerAvisos {

    private final ProcesadorAvisos procesador;

    public ListenerAvisos(ProcesadorAvisos procesador) {
        this.procesador = procesador;
    }

    @RabbitListener(queues = TopologiaRabbit.COLA)
    public void recibir(Message mensaje) {
        procesador.procesar(new String(mensaje.getBody(), StandardCharsets.UTF_8));
    }
}
