package ar.edu.uade.reclamos.prioridad.proveedor;

import java.util.List;

/**
 * La forma de la respuesta que se le pide a cualquier modelo: un objeto con {@code prioridad} (uno de cuatro
 * valores) y {@code motivo}. Cada adaptador la escribe en el formato de esquema de su API.
 */
public final class EsquemaRespuesta {

    public static final List<String> PRIORIDADES = List.of("BAJA", "MEDIA", "ALTA", "CRITICA");
    public static final String CAMPO_PRIORIDAD = "prioridad";
    public static final String CAMPO_MOTIVO = "motivo";

    private EsquemaRespuesta() {
    }
}
