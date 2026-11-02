package ar.edu.uade.reclamos.notificaciones.aviso;

/**
 * El mensaje no cumple el contrato: no es JSON, le falta un campo o tiene una versión desconocida.
 * Reintentarlo no lo arregla, así que va directo a la cola de errores.
 */
public class MensajeInvalidoException extends RuntimeException {

    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
