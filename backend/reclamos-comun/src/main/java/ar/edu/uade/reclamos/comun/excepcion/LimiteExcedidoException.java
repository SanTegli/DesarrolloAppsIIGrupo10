package ar.edu.uade.reclamos.comun.excepcion;

/** Se superó un límite de uso (reclamos por día, códigos por hora). HTTP 429. */
public class LimiteExcedidoException extends ReclamosException {
    public LimiteExcedidoException(String mensaje) {
        super(mensaje);
    }
}
