package ar.edu.uade.reclamos.dominio.modelo;

/**
 * Quién ejecuta una acción sobre un reclamo.
 * {@link #SISTEMA} no corresponde a ningún usuario: representa las acciones automáticas
 * (asignación al crear el reclamo, marcado de vencidos).
 */
public enum Rol {
    CIUDADANO,
    AGENTE_MUNICIPAL,
    ADMINISTRADOR,
    SISTEMA
}
