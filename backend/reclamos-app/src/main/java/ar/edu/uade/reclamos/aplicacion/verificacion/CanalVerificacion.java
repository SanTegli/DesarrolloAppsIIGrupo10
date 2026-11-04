package ar.edu.uade.reclamos.aplicacion.verificacion;

/**
 * Por dónde confirma el ciudadano su teléfono (Adapter). El caso de uso no sabe si es Telegram o el modo
 * simulado; WhatsApp sería otra implementación.
 */
public interface CanalVerificacion {

    /** {@code telegram} o {@code simulado}: la interfaz muestra un enlace o un formulario según esto. */
    String nombre();

    /** Enlace que abre el canal con el código ya cargado; {@code null} si el canal no tiene enlace. */
    String enlace(String codigo);
}
