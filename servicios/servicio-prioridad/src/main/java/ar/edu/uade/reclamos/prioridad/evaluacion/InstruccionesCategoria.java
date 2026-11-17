package ar.edu.uade.reclamos.prioridad.evaluacion;

import ar.edu.uade.reclamos.prioridad.proveedor.ConsultaIA;
import ar.edu.uade.reclamos.prioridad.proveedor.FormatoRespuesta;
import java.util.List;

/**
 * Arma la consulta para sugerir la categoría de un reclamo de "Otro problema" (PR 26): las instrucciones de
 * {@code instrucciones-categoria.txt}, lo que escribió el vecino como dato delimitado y las categorías posibles
 * como valores del esquema de respuesta. Contra la inyección de instrucciones vale lo mismo que en
 * {@link InstruccionesPrioridad}.
 */
public class InstruccionesCategoria {

    private final String instrucciones;

    public InstruccionesCategoria(String instrucciones) {
        if (instrucciones == null || instrucciones.isBlank()) {
            throw new IllegalStateException("Las instrucciones de categoría están vacías");
        }
        this.instrucciones = instrucciones.strip();
    }

    public ConsultaIA armar(String propuesta, String descripcion, List<String> categorias) {
        String reclamo = "<propuesta>" + neutralizar(propuesta) + "</propuesta>\n"
                + "<descripcion>\n" + neutralizar(descripcion) + "\n</descripcion>";
        return new ConsultaIA(instrucciones, reclamo, FormatoRespuesta.categoria(categorias));
    }

    private static String neutralizar(String texto) {
        return texto.strip().replace('<', '‹').replace('>', '›');
    }
}
