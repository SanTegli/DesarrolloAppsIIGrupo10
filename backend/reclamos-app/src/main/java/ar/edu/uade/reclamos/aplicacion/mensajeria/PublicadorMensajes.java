package ar.edu.uade.reclamos.aplicacion.mensajeria;

import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * La otra mitad de la outbox: cada {@code reclamos.mensajeria.intervalo-ms} toma los mensajes pendientes, los
 * publica en RabbitMQ y los marca publicados cuando el broker confirma.
 *
 * <p>Si el broker no responde, el mensaje queda pendiente con el error y la pasada se corta: los demás se
 * intentan en la próxima, en el mismo orden. Un mensaje se puede publicar dos veces si el backend cae entre la
 * confirmación y la marca; el consumidor descarta los repetidos por {@code eventId}.
 */
public class PublicadorMensajes {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicadorMensajes.class);

    private final MensajeSalienteRepository mensajes;
    private final BrokerMensajes broker;
    private final Clock reloj;
    private final int lote;

    public PublicadorMensajes(MensajeSalienteRepository mensajes, BrokerMensajes broker, Clock reloj, int lote) {
        this.mensajes = mensajes;
        this.broker = broker;
        this.reloj = reloj;
        this.lote = lote;
    }

    /** @return cuántos mensajes quedaron publicados en esta pasada */
    @Scheduled(fixedDelayString = "${reclamos.mensajeria.intervalo-ms:2000}")
    public int publicarPendientes() {
        List<MensajeSaliente> pendientes = mensajes.buscarPendientes(lote);
        int publicados = 0;
        for (MensajeSaliente mensaje : pendientes) {
            try {
                broker.publicar(mensaje);
            } catch (RuntimeException error) {
                mensaje.registrarFalla(error.getMessage());
                mensajes.guardar(mensaje);
                LOGGER.warn("No se pudo publicar {} ({} intentos): {}. Quedan {} pendientes en esta pasada",
                        mensaje.getEventId(), mensaje.getIntentos(), error.getMessage(),
                        pendientes.size() - publicados);
                return publicados;
            }
            mensaje.marcarPublicado(LocalDateTime.now(reloj));
            mensajes.guardar(mensaje);
            publicados++;
        }
        if (publicados > 0) {
            LOGGER.info("Publicados {} mensajes en RabbitMQ", publicados);
        }
        return publicados;
    }
}
