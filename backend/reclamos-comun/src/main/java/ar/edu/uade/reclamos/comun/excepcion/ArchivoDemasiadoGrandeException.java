package ar.edu.uade.reclamos.comun.excepcion;

/** Un archivo subido supera el tamaño o las dimensiones permitidas. HTTP 413. */
public class ArchivoDemasiadoGrandeException extends ReclamosException {
    public ArchivoDemasiadoGrandeException(String mensaje) {
        super(mensaje);
    }
}
