package ar.edu.uade.reclamos.prioridad.proveedor;

/**
 * Lo que se le manda al modelo, igual para todos los proveedores.
 *
 * @param instrucciones criterios y reglas de respuesta (mensaje de sistema)
 * @param reclamo       los datos del reclamo, delimitados como datos (mensaje del usuario)
 * @param formato       qué campo y qué valores tiene que contestar
 */
public record ConsultaIA(String instrucciones, String reclamo, FormatoRespuesta formato) {

    /** Consulta de prioridad, la original del servicio. */
    public ConsultaIA(String instrucciones, String reclamo) {
        this(instrucciones, reclamo, FormatoRespuesta.PRIORIDAD);
    }
}
