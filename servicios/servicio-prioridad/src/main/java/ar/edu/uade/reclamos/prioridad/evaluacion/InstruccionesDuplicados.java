package ar.edu.uade.reclamos.prioridad.evaluacion;

import ar.edu.uade.reclamos.prioridad.proveedor.ConsultaIA;
import ar.edu.uade.reclamos.prioridad.proveedor.FormatoRespuesta;
import java.util.List;

/**
 * Arma la consulta para comparar un reclamo nuevo con los reclamos abiertos cercanos (PR 27): las instrucciones de
 * {@code instrucciones-duplicados.txt}, los reclamos como datos delimitados y los ids de los candidatos como
 * valores del esquema de respuesta. Los textos los escribieron vecinos: se neutralizan {@code <} y {@code >} para
 * que nadie pueda cerrar una etiqueta y escribir instrucciones fuera de ella.
 */
public class InstruccionesDuplicados {

    private final String instrucciones;

    public InstruccionesDuplicados(String instrucciones) {
        if (instrucciones == null || instrucciones.isBlank()) {
            throw new IllegalStateException("Las instrucciones de duplicados están vacías");
        }
        this.instrucciones = instrucciones.strip();
    }

    public ConsultaIA armar(ReclamoComparado nuevo, List<ReclamoComparado> candidatos) {
        StringBuilder texto = new StringBuilder("<nuevo>\n").append(cuerpo(nuevo)).append("</nuevo>\n");
        for (ReclamoComparado candidato : candidatos) {
            texto.append("<candidato id=\"").append(neutralizar(candidato.id())).append("\">\n")
                    .append(cuerpo(candidato)).append("</candidato>\n");
        }
        return new ConsultaIA(instrucciones, texto.toString().strip(),
                FormatoRespuesta.similar(candidatos.stream().map(ReclamoComparado::id).toList()));
    }

    private static String cuerpo(ReclamoComparado reclamo) {
        return "categoria: " + neutralizar(reclamo.categoria()) + "\ndescripcion: "
                + neutralizar(reclamo.descripcion()) + "\n";
    }

    private static String neutralizar(String texto) {
        return texto == null ? "" : texto.strip().replace('<', '‹').replace('>', '›');
    }
}
