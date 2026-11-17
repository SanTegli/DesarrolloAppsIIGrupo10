package ar.edu.uade.reclamos.prioridad.evaluacion;

import ar.edu.uade.reclamos.prioridad.proveedor.FormatoRespuesta;
import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaInvalidaException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compara un reclamo nuevo con los reclamos abiertos que el backend encontró a pocos metros (PR 27) y dice cuál
 * describe el mismo problema, o ninguno. Solo con texto: categoría y descripción, sin fotos ni datos de los
 * vecinos. Como siempre, no se confía en el modelo: el id tiene que ser uno de los recibidos.
 */
public class ComparadorDuplicados {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComparadorDuplicados.class);

    private final InstruccionesDuplicados instrucciones;
    private final ProveedorIA proveedor;

    public ComparadorDuplicados(InstruccionesDuplicados instrucciones, ProveedorIA proveedor) {
        this.instrucciones = instrucciones;
        this.proveedor = proveedor;
    }

    public ComparacionDuplicados comparar(ReclamoComparado nuevo, List<ReclamoComparado> candidatos) {
        long inicio = System.nanoTime();
        RespuestaIA respuesta = proveedor.sugerir(instrucciones.armar(nuevo, candidatos));
        String similar = elegido(respuesta.valor(), candidatos);
        String motivo = EvaluadorPrioridad.acotar(proveedor.nombre(), respuesta.motivo());
        LOGGER.info("Duplicado {} según {} ({}) en {} ms entre {} candidatos", similar == null ? "ninguno" : similar,
                proveedor.nombre(), proveedor.modelo(), (System.nanoTime() - inicio) / 1_000_000, candidatos.size());
        return new ComparacionDuplicados(similar, motivo, proveedor.nombre(), proveedor.modelo());
    }

    private String elegido(String valor, List<ReclamoComparado> candidatos) {
        if (FormatoRespuesta.NINGUNO.equals(valor)) {
            return null;
        }
        if (candidatos.stream().noneMatch(candidato -> candidato.id().equals(valor))) {
            throw new RespuestaInvalidaException(proveedor.nombre() + " eligió un reclamo que no estaba entre los cercanos");
        }
        return valor;
    }
}
