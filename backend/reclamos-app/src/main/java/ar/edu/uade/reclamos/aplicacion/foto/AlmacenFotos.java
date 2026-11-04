package ar.edu.uade.reclamos.aplicacion.foto;

import java.util.Optional;

/**
 * Dónde se guardan las fotos (patrón Adapter). La foto se guarda en dos pasos porque el archivo y la base no
 * comparten transacción: primero como pendiente y, cuando el reclamo ya está en la base, se confirma. Si el
 * alta falla, se descarta.
 *
 * <p>{@link AlmacenFotosLocal} usa un directorio (un volumen en Compose). Firebase Storage o S3 serían otra
 * implementación, sin cambiar el caso de uso.
 */
public interface AlmacenFotos {

    /** Guarda la foto como pendiente y devuelve su referencia, que no revela nada del reclamo ni del autor. */
    String guardarPendiente(byte[] jpeg);

    /** La pasa de pendiente a definitiva. */
    void confirmar(String referencia);

    /** Borra una foto pendiente; si no existe, no hace nada. */
    void descartar(String referencia);

    /** La foto confirmada, o vacío si no existe o la referencia no es válida. */
    Optional<byte[]> leer(String referencia);
}
