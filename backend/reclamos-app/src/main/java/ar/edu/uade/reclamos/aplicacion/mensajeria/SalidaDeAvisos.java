package ar.edu.uade.reclamos.aplicacion.mensajeria;

import ar.edu.uade.reclamos.dominio.modelo.Notificacion;

/**
 * Por dónde salen los avisos hacia otros sistemas, además de quedar guardados como avisos internos.
 * {@code ServicioNotificaciones} la llama por cada aviso, dentro de la transacción del caso de uso.
 */
public interface SalidaDeAvisos {

    /** Sin mensajería ({@code reclamos.mensajeria.habilitada: false}): los avisos quedan solo internos. */
    SalidaDeAvisos NINGUNA = (aviso, tipo) -> { };

    void enviar(Notificacion aviso, TipoAviso tipo);
}
