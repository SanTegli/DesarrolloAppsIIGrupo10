package ar.edu.uade.reclamos.comun.excepcion;

/** Los datos recibidos no cumplen el formato o son incompletos. HTTP 400. */
public class DatosInvalidosException extends ReclamosException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
