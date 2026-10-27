package ar.edu.uade.reclamos.dominio.modelo;

/** Qué decidió la prioridad de un reclamo. Gestión lo ve para saber cuánto confiar en ella. */
public enum OrigenPrioridad {
    /** La prioridad base de la categoría. */
    CATEGORIA,
    /** La descripción contenía una palabra de urgencia. */
    PALABRAS_CLAVE,
    /** La sugirió el servicio de prioridad con IA. */
    IA,
    /** La IA no respondió y se usó la prioridad de la categoría. */
    RESPALDO
}
