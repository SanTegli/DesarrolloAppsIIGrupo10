package ar.edu.uade.reclamos.aplicacion.categoria;

/**
 * Pide que la IA sugiera la categoría de un reclamo de "Otro problema". Se llama dentro de la transacción del
 * caso de uso: el pedido queda guardado o se pierde junto con él.
 */
public interface SolicitudesSugerenciaCategoria {

    /** Sin mensajería o sin la estrategia {@code ia}: el administrador elige la categoría sin sugerencia. */
    SolicitudesSugerenciaCategoria NINGUNA = new SolicitudesSugerenciaCategoria() {
        @Override
        public void solicitar(String reclamoNumero) {
        }

        @Override
        public void reintentar(String reclamoNumero, int intento) {
        }
    };

    /** Primer intento, apenas se publique el mensaje. */
    void solicitar(String reclamoNumero);

    /** Otro intento, después de la espera configurada. */
    void reintentar(String reclamoNumero, int intento);
}
