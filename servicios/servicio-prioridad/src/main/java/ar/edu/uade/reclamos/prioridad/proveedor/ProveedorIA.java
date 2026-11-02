package ar.edu.uade.reclamos.prioridad.proveedor;

/**
 * Lo que el servicio necesita de un modelo de lenguaje: dada una consulta, una prioridad y un motivo.
 *
 * <p>Cada proveedor tiene su propia API (URL, autenticación, formato del pedido, dónde viene el texto de la
 * respuesta). Cada implementación es un <b>Adapter</b> que traduce esta interfaz a la API de su proveedor;
 * {@code EvaluadorPrioridad} no sabe cuál está usando. Se elige con {@code prioridad.ia.proveedor}.
 */
public interface ProveedorIA {

    /** Nombre que se devuelve en la respuesta: {@code gemini}, {@code openai} o {@code simulado}. */
    String nombre();

    /** Modelo configurado, por ejemplo {@code gemini-3.5-flash-lite}. */
    String modelo();

    /**
     * Pide la sugerencia al modelo. La respuesta viene tal como la dio el modelo: la valida quien llama.
     *
     * @throws ProveedorNoDisponibleException si el proveedor no está configurado, rechaza el pedido o falla
     * @throws TiempoAgotadoException si no responde dentro del tiempo límite
     * @throws RespuestaInvalidaException si responde algo que no se puede leer como la respuesta pedida
     */
    RespuestaIA sugerir(ConsultaIA consulta);
}
