package ar.edu.uade.reclamos.comun.excepcion;

/** El usuario existe pero no tiene permiso para ejecutar la operación. HTTP 403. */
public class AccesoDenegadoException extends ReclamosException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
