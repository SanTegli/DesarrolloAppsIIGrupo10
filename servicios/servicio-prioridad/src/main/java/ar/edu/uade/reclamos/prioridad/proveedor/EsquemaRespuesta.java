package ar.edu.uade.reclamos.prioridad.proveedor;

import java.util.List;

/**
 * Nombres de los campos de la respuesta que se le pide a cualquier modelo. La forma completa (qué campo y qué
 * valores) está en {@link FormatoRespuesta}.
 */
public final class EsquemaRespuesta {

    public static final List<String> PRIORIDADES = List.of("BAJA", "MEDIA", "ALTA", "CRITICA");
    public static final String CAMPO_PRIORIDAD = "prioridad";
    public static final String CAMPO_CATEGORIA = "categoria";
    public static final String CAMPO_MOTIVO = "motivo";

    private EsquemaRespuesta() {
    }
}
