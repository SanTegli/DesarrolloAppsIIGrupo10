package ar.edu.uade.reclamos.prioridad.proveedor;

/** El proveedor de IA no respondió dentro de {@code prioridad.ia.tiempo-limite}. Se responde 504. */
public class TiempoAgotadoException extends RuntimeException {

    public TiempoAgotadoException(String mensaje) {
        super(mensaje);
    }

    public TiempoAgotadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
