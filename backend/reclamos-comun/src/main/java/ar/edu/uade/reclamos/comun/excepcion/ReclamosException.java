package ar.edu.uade.reclamos.comun.excepcion;

/**
 * Base de todos los errores de negocio del sistema.
 * El manejador global de errores traduce cada subclase a un código HTTP.
 */
public abstract class ReclamosException extends RuntimeException {

    protected ReclamosException(String mensaje) {
        super(mensaje);
    }
}
