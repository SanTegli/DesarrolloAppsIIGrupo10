package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.time.LocalDateTime;

/**
 * Un aviso que tiene que salir por la cola de mensajes (patrón <b>Outbox</b>). Se guarda en la misma
 * transacción que el aviso: si la base confirma, el mensaje no se pierde aunque el broker esté caído. Una
 * tarea lo publica después y recién entonces lo marca {@link EstadoMensaje#PUBLICADO}.
 *
 * <p>No tiene reglas de negocio: es el registro de lo que falta enviar. El {@code eventId} es único y viaja
 * en el mensaje, para que el consumidor reconozca los repetidos.
 */
public class MensajeSaliente extends Entidad {
    public static final int LONGITUD_MAXIMA_CONTENIDO = 4000;
    public static final int LONGITUD_MAXIMA_ERROR = 300;

    private String eventId;
    private String claveRuteo;
    private String tipoEvento;
    private String contenido;
    private EstadoMensaje estado;
    private int intentos;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaPublicacion;
    private String ultimoError;

    /** Requerido por JPA. */
    protected MensajeSaliente() {
    }

    /**
     * @param contenido el mensaje ya serializado (JSON), tal como se publica
     */
    public MensajeSaliente(String eventId, String claveRuteo, String tipoEvento, String contenido,
                           LocalDateTime fechaCreacion) {
        this.eventId = Validador.requerirTexto(eventId, "eventId", 36);
        this.claveRuteo = Validador.requerirTexto(claveRuteo, "claveRuteo", 60);
        this.tipoEvento = Validador.requerirTexto(tipoEvento, "tipoEvento", 40);
        this.contenido = Validador.requerirTexto(contenido, "contenido", LONGITUD_MAXIMA_CONTENIDO);
        this.fechaCreacion = Validador.requerirNoNulo(fechaCreacion, "fechaCreacion");
        this.estado = EstadoMensaje.PENDIENTE;
    }

    /** El broker confirmó que lo recibió. */
    public void marcarPublicado(LocalDateTime fecha) {
        this.estado = EstadoMensaje.PUBLICADO;
        this.fechaPublicacion = Validador.requerirNoNulo(fecha, "fecha");
        this.ultimoError = null;
    }

    /** No se pudo publicar: sigue pendiente y se vuelve a intentar en la próxima pasada. */
    public void registrarFalla(String error) {
        this.intentos++;
        String texto = error == null || error.isBlank() ? "Error sin detalle" : error.strip();
        this.ultimoError = texto.length() <= LONGITUD_MAXIMA_ERROR ? texto
                : texto.substring(0, LONGITUD_MAXIMA_ERROR);
    }

    public boolean isPendiente() {
        return estado == EstadoMensaje.PENDIENTE;
    }

    public String getEventId() {
        return eventId;
    }

    public String getClaveRuteo() {
        return claveRuteo;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getContenido() {
        return contenido;
    }

    public EstadoMensaje getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /** {@code null} mientras está pendiente. */
    public LocalDateTime getFechaPublicacion() {
        return fechaPublicacion;
    }

    /** {@code null} si nunca falló o si ya se publicó. */
    public String getUltimoError() {
        return ultimoError;
    }
}
