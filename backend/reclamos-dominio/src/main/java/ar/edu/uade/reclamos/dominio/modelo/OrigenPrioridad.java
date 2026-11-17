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
    RESPALDO,
    /**
     * La IA no respondió a tiempo durante el alta: por ahora vale la prioridad de la categoría y la IA lo
     * vuelve a revisar en segundo plano. Termina en {@link #IA}, {@link #CATEGORIA} o {@link #RESPALDO}.
     */
    PENDIENTE_IA
}
