package ar.edu.uade.reclamos.prioridad.proveedor;

import java.util.List;

/**
 * Qué se le pide al modelo que conteste: un objeto con {@code campo} (uno de {@code valores}) y {@code motivo}.
 * Cada adaptador lo escribe en el formato de esquema de su API. Hay tres: la prioridad, fija; la categoría, que
 * cambia según las categorías que manda el backend (PR 26); y el reclamo más parecido, entre los candidatos que
 * manda el backend (PR 27).
 *
 * @param nombre nombre del esquema (OpenAI lo pide); letras, números y guiones bajos
 */
public record FormatoRespuesta(String nombre, String campo, List<String> valores) {

    public static final FormatoRespuesta PRIORIDAD = new FormatoRespuesta("prioridad_reclamo",
            EsquemaRespuesta.CAMPO_PRIORIDAD, EsquemaRespuesta.PRIORIDADES);

    /** Valor que contesta el modelo cuando ninguna categoría corresponde. */
    public static final String NINGUNA = "NINGUNA";

    /** Valor que contesta el modelo cuando ningún reclamo cercano es el mismo problema. */
    public static final String NINGUNO = "NINGUNO";

    public FormatoRespuesta {
        valores = List.copyOf(valores);
        if (valores.isEmpty()) {
            throw new IllegalArgumentException("El formato de respuesta necesita al menos un valor");
        }
    }

    /** Una de las categorías, o {@link #NINGUNA}. */
    public static FormatoRespuesta categoria(List<String> categorias) {
        List<String> valores = new java.util.ArrayList<>(categorias);
        valores.add(NINGUNA);
        return new FormatoRespuesta("categoria_reclamo", EsquemaRespuesta.CAMPO_CATEGORIA, valores);
    }

    /** El id de uno de los candidatos, o {@link #NINGUNO}. */
    public static FormatoRespuesta similar(List<String> ids) {
        List<String> valores = new java.util.ArrayList<>(ids);
        valores.add(NINGUNO);
        return new FormatoRespuesta("duplicado_reclamo", EsquemaRespuesta.CAMPO_SIMILAR, valores);
    }
}
