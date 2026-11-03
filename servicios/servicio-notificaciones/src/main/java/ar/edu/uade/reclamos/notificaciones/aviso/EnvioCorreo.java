package ar.edu.uade.reclamos.notificaciones.aviso;

/** Manda un aviso por correo. Si falla, lanza la excepción: el mensaje se reintenta. */
public interface EnvioCorreo {

    void enviar(MensajeAviso aviso);
}
