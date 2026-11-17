package ar.edu.uade.reclamos.prioridad.proveedor;

/**
 * Lo que contestó el modelo, sin validar: el valor pedido (una prioridad o una categoría) y el motivo, como
 * texto.
 */
public record RespuestaIA(String valor, String motivo) {

    /** El valor de una consulta de prioridad. */
    public String prioridad() {
        return valor;
    }
}
