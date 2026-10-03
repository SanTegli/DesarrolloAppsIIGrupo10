package ar.edu.uade.reclamos.dominio.modelo;

/** Ciclo de vida del reclamo. Las transiciones permitidas están en {@link PoliticaTransiciones}. */
public enum EstadoReclamo {
    INGRESADO,
    ASIGNADO,
    EN_PROCESO,
    RESUELTO,
    CERRADO,
    RECHAZADO,
    CANCELADO;

    /** Un estado final no admite más cambios. */
    public boolean esFinal() {
        return this == CERRADO || this == RECHAZADO || this == CANCELADO;
    }

    /** El reclamo todavía espera que alguien lo resuelva: cuenta para la carga de trabajo y puede vencer. */
    public boolean estaPendienteDeResolucion() {
        return this == INGRESADO || this == ASIGNADO || this == EN_PROCESO;
    }
}
