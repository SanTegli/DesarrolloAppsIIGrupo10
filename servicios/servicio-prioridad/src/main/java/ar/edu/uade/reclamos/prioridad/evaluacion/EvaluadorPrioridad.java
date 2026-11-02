package ar.edu.uade.reclamos.prioridad.evaluacion;

import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El caso de uso del servicio: arma la consulta, se la pasa al proveedor configurado y valida lo que
 * vuelve. Aunque se pida salida estructurada, nunca se confía en el modelo: la prioridad tiene que ser una
 * de las cuatro y el motivo no puede venir vacío.
 */
public class EvaluadorPrioridad {

    public static final int LONGITUD_MAXIMA_MOTIVO = 300;
    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluadorPrioridad.class);

    private final InstruccionesPrioridad instrucciones;
    private final ProveedorIA proveedor;

    public EvaluadorPrioridad(InstruccionesPrioridad instrucciones, ProveedorIA proveedor) {
        this.instrucciones = instrucciones;
        this.proveedor = proveedor;
    }

    public Evaluacion evaluar(String categoria, String descripcion) {
        long inicio = System.nanoTime();
        RespuestaIA respuesta = proveedor.sugerir(instrucciones.armar(categoria, descripcion));
        Prioridad prioridad = Prioridad.leer(respuesta.prioridad()).orElseThrow(() ->
                new RespuestaInvalidaException(proveedor.nombre() + " sugirió una prioridad que no existe"));
        String motivo = acotar(respuesta.motivo());
        // Sin la descripción en el log: solo su largo, para medir sin guardar lo que escribió el vecino.
        LOGGER.info("Prioridad {} sugerida por {} ({}) en {} ms; descripción de {} caracteres", prioridad,
                proveedor.nombre(), proveedor.modelo(), (System.nanoTime() - inicio) / 1_000_000,
                descripcion.length());
        return new Evaluacion(prioridad, motivo, proveedor.nombre(), proveedor.modelo());
    }

    private String acotar(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RespuestaInvalidaException(proveedor.nombre() + " no explicó el motivo");
        }
        String limpio = motivo.strip().replaceAll("\\s+", " ");
        if (limpio.length() <= LONGITUD_MAXIMA_MOTIVO) {
            return limpio;
        }
        return limpio.substring(0, LONGITUD_MAXIMA_MOTIVO - 1).stripTrailing() + "…";
    }
}
