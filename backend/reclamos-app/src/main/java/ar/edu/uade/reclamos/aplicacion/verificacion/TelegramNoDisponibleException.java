package ar.edu.uade.reclamos.aplicacion.verificacion;

/** Telegram no respondió o respondió con error. A propósito no guarda la causa: incluiría el token. */
public class TelegramNoDisponibleException extends RuntimeException {
    public TelegramNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
