package ar.edu.uade.reclamos.comun.excepcion;

/** La operación es válida en formato pero viola una regla del negocio. HTTP 409. */
public class ReglaNegocioException extends ReclamosException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
