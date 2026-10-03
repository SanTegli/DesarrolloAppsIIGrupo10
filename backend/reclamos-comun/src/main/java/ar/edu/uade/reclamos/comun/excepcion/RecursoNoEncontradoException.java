package ar.edu.uade.reclamos.comun.excepcion;

/** El recurso pedido no existe. HTTP 404. */
public class RecursoNoEncontradoException extends ReclamosException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public RecursoNoEncontradoException(String recurso, Object identificador) {
        super("No existe " + recurso + " con identificador " + identificador);
    }
}
