package ar.edu.uade.reclamos.prioridad.evaluacion;

import java.util.Locale;
import java.util.Optional;

/** Las cuatro prioridades del sistema, las mismas que usa el backend. */
public enum Prioridad {
    BAJA, MEDIA, ALTA, CRITICA;

    /** Lee lo que escribió el modelo, tolerando espacios y minúsculas; vacío si no es una prioridad. */
    public static Optional<Prioridad> leer(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(texto.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException desconocida) {
            return Optional.empty();
        }
    }
}
