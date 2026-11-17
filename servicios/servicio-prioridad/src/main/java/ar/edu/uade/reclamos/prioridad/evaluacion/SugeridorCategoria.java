package ar.edu.uade.reclamos.prioridad.evaluacion;

import ar.edu.uade.reclamos.prioridad.proveedor.FormatoRespuesta;
import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaInvalidaException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sugiere la categoría de un reclamo que el vecino cargó como "Otro problema" (PR 26). Como con la prioridad,
 * no se confía en el modelo: la categoría tiene que ser una de las recibidas (o ninguna) y el motivo no puede
 * venir vacío. Es solo una sugerencia: la recategorización la decide un administrador.
 */
public class SugeridorCategoria {

    private static final Logger LOGGER = LoggerFactory.getLogger(SugeridorCategoria.class);

    private final InstruccionesCategoria instrucciones;
    private final ProveedorIA proveedor;

    public SugeridorCategoria(InstruccionesCategoria instrucciones, ProveedorIA proveedor) {
        this.instrucciones = instrucciones;
        this.proveedor = proveedor;
    }

    public SugerenciaCategoria sugerir(String propuesta, String descripcion, List<String> categorias) {
        long inicio = System.nanoTime();
        RespuestaIA respuesta = proveedor.sugerir(instrucciones.armar(propuesta, descripcion, categorias));
        String categoria = elegida(respuesta.valor(), categorias).orElse(null);
        String motivo = EvaluadorPrioridad.acotar(proveedor.nombre(), respuesta.motivo());
        LOGGER.info("Categoría {} sugerida por {} ({}) en {} ms entre {} categorías", categoria == null ? "ninguna"
                : categoria, proveedor.nombre(), proveedor.modelo(), (System.nanoTime() - inicio) / 1_000_000,
                categorias.size());
        return new SugerenciaCategoria(categoria, motivo, proveedor.nombre(), proveedor.modelo());
    }

    /** Vacío si contestó NINGUNA; error si contestó algo que no estaba entre las opciones. */
    private Optional<String> elegida(String valor, List<String> categorias) {
        if (FormatoRespuesta.NINGUNA.equals(valor)) {
            return Optional.empty();
        }
        return categorias.stream().filter(categoria -> categoria.equals(valor)).findFirst()
                .or(() -> {
                    throw new RespuestaInvalidaException(proveedor.nombre() + " sugirió una categoría que no existe");
                });
    }
}
