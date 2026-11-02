package ar.edu.uade.reclamos.prioridad.proveedor;

/** El modelo respondió, pero no con una prioridad válida y un motivo. Se responde 502. */
public class RespuestaInvalidaException extends RuntimeException {

    public RespuestaInvalidaException(String mensaje) {
        super(mensaje);
    }

    public RespuestaInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
