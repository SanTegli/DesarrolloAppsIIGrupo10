package ar.edu.uade.reclamos.aplicacion.foto;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La foto del reclamo: validarla, re-codificarla y guardarla en dos pasos. Quien crea el reclamo llama a
 * {@link #preparar} antes del alta, a {@link #confirmar} cuando la base ya la guardó y a {@link #descartar} si
 * el alta falló.
 *
 * <p>La foto nunca sale del backend: no viaja a la IA ni a RabbitMQ, y solo se sirve con los mismos permisos
 * que el detalle del reclamo.
 */
public class ServicioFotos {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioFotos.class);

    private final ProcesadorFotos procesador;
    private final AlmacenFotos almacen;

    public ServicioFotos(ProcesadorFotos procesador, AlmacenFotos almacen) {
        this.procesador = procesador;
        this.almacen = almacen;
    }

    /** Valida la foto, la re-codifica y la guarda como pendiente. */
    public String preparar(FotoSubida foto) {
        if (foto == null) {
            throw new DatosInvalidosException("El reclamo necesita una foto del problema.");
        }
        return almacen.guardarPendiente(procesador.procesar(foto));
    }

    /**
     * El reclamo ya está en la base. Si mover el archivo falla, el alta no se deshace: el reclamo queda sin
     * foto visible (404) y el error queda en el log.
     */
    public void confirmar(String referencia) {
        try {
            almacen.confirmar(referencia);
        } catch (UncheckedIOException error) {
            LOGGER.error("El reclamo se creó, pero no se pudo confirmar su foto {}", referencia, error);
        }
    }

    /** El alta falló: la foto pendiente se borra. */
    public void descartar(String referencia) {
        try {
            almacen.descartar(referencia);
        } catch (UncheckedIOException error) {
            LOGGER.warn("No se pudo borrar la foto pendiente {}", referencia, error);
        }
    }

    public Optional<byte[]> leer(String referencia) {
        return almacen.leer(referencia);
    }
}
