package ar.edu.uade.reclamos.dominio.modelo;

/** Si la dirección del reclamo ya tiene coordenadas confirmadas por un geocodificador. */
public enum EstadoUbicacion {
    /** El ciudadano eligió una sugerencia del geocodificador: hay coordenadas. */
    CONFIRMADA,
    /** Solo está la dirección escrita: el geocodificador no respondió o nadie eligió una sugerencia. */
    PENDIENTE
}
