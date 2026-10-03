package ar.edu.uade.reclamos.dominio.modelo;

/** Prioridad de atención, de menor a mayor. */
public enum Prioridad {
    BAJA,
    MEDIA,
    ALTA,
    CRITICA;

    /** La prioridad inmediatamente superior; {@link #CRITICA} se queda en {@link #CRITICA}. */
    public Prioridad siguiente() {
        return this == CRITICA ? CRITICA : values()[ordinal() + 1];
    }

    public boolean esMayorQue(Prioridad otra) {
        return compareTo(otra) > 0;
    }

    public static Prioridad mayor(Prioridad a, Prioridad b) {
        return a.esMayorQue(b) ? a : b;
    }
}
