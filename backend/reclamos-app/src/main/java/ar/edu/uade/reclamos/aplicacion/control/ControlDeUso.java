package ar.edu.uade.reclamos.aplicacion.control;

import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import java.time.LocalDateTime;

/**
 * Un eslabón de la cadena de controles antes de crear o reabrir un reclamo (Chain of Responsibility). Cada
 * control revisa una sola cosa: si no se cumple, corta la cadena con una excepción; si se cumple, le pasa el
 * pedido al siguiente. Agregar o reordenar controles no toca el caso de uso.
 */
public abstract class ControlDeUso {

    private ControlDeUso siguiente;

    /** Encadena {@code siguiente} después de este control y lo devuelve, para seguir encadenando. */
    public ControlDeUso luego(ControlDeUso siguiente) {
        this.siguiente = siguiente;
        return siguiente;
    }

    public final void verificar(Ciudadano ciudadano, LocalDateTime ahora) {
        revisar(ciudadano, ahora);
        if (siguiente != null) {
            siguiente.verificar(ciudadano, ahora);
        }
    }

    /** Lanza una excepción si el ciudadano no pasa este control. */
    protected abstract void revisar(Ciudadano ciudadano, LocalDateTime ahora);
}
