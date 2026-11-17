package ar.edu.uade.reclamos.aplicacion.prioridad;

/**
 * Pide que la IA vuelva a revisar la prioridad de un reclamo más tarde. Se llama dentro de la transacción del
 * caso de uso: el pedido queda guardado o se pierde junto con él.
 */
public interface SolicitudesReevaluacion {

    /** Sin mensajería o sin la estrategia {@code ia}: no hay segunda oportunidad. */
    SolicitudesReevaluacion NINGUNA = new SolicitudesReevaluacion() {
        @Override
        public void solicitar(String reclamoNumero) {
        }

        @Override
        public void reintentar(String reclamoNumero, int intento) {
        }
    };

    /** Primer intento en segundo plano, apenas se publique el mensaje. */
    void solicitar(String reclamoNumero);

    /** Otro intento, después de la espera configurada. */
    void reintentar(String reclamoNumero, int intento);
}
