package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.time.LocalDateTime;

/** Aviso enviado a un usuario sobre un reclamo. Queda guardado como evidencia del envío. */
public class Notificacion extends Entidad {

    private Reclamo reclamo;
    private Usuario destinatario;
    private CanalNotificacion canal;
    private String mensaje;
    private LocalDateTime fechaEnvio;

    /** Requerido por JPA. */
    protected Notificacion() {
    }

    public Notificacion(Reclamo reclamo, Usuario destinatario, CanalNotificacion canal, String mensaje,
                        LocalDateTime fechaEnvio) {
        this.reclamo = Validador.requerirNoNulo(reclamo, "reclamo");
        this.destinatario = Validador.requerirNoNulo(destinatario, "destinatario");
        this.canal = Validador.requerirNoNulo(canal, "canal");
        this.mensaje = Validador.requerirTexto(mensaje, "mensaje", 500);
        this.fechaEnvio = Validador.requerirNoNulo(fechaEnvio, "fechaEnvio");
    }

    public Reclamo getReclamo() {
        return reclamo;
    }

    public Usuario getDestinatario() {
        return destinatario;
    }

    public CanalNotificacion getCanal() {
        return canal;
    }

    public String getMensaje() {
        return mensaje;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }
}
