package ar.edu.uade.reclamos.dominio.modelo;

/** Si un mensaje de la outbox ya llegó al broker. */
public enum EstadoMensaje {
    PENDIENTE,
    PUBLICADO
}
