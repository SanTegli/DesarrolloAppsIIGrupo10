package ar.edu.uade.reclamos.aplicacion.mensajeria;

/**
 * Qué evento de dominio generó un aviso, y con qué clave de ruteo sale a RabbitMQ. Los valores están en
 * {@code docs/contratos/mensajes.md}.
 */
public enum TipoAviso {
    RECLAMO_CREADO("ReclamoCreado", "aviso.reclamo-creado"),
    RECLAMO_ASIGNADO("ReclamoAsignado", "aviso.reclamo-asignado"),
    ESTADO_CAMBIADO("EstadoReclamoCambiado", "aviso.estado-cambiado"),
    RECLAMO_RESUELTO("ReclamoResuelto", "aviso.reclamo-resuelto"),
    RECLAMO_VENCIDO("ReclamoVencido", "aviso.reclamo-vencido");

    private final String evento;
    private final String claveRuteo;

    TipoAviso(String evento, String claveRuteo) {
        this.evento = evento;
        this.claveRuteo = claveRuteo;
    }

    /** Nombre del evento de dominio, el campo {@code tipoEvento} del mensaje. */
    public String evento() {
        return evento;
    }

    public String claveRuteo() {
        return claveRuteo;
    }
}
