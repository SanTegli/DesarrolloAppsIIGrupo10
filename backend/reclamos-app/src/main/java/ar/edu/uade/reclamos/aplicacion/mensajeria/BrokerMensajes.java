package ar.edu.uade.reclamos.aplicacion.mensajeria;

import ar.edu.uade.reclamos.dominio.modelo.MensajeSaliente;

/** El broker visto desde la outbox: publicar un mensaje y esperar a que confirme que lo recibió. */
public interface BrokerMensajes {

    /**
     * @throws RuntimeException si el broker no está disponible o no confirmó a tiempo; el mensaje sigue
     *         pendiente
     */
    void publicar(MensajeSaliente mensaje);
}
