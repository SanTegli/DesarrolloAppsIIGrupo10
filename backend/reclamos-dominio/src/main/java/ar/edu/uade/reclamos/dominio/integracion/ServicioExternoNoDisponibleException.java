package ar.edu.uade.reclamos.dominio.integracion;

/**
 * Un servicio externo (cobertura, geocodificador, IA) no respondió a tiempo o respondió algo inválido.
 * No es un error del usuario: quien la recibe aplica el modo degradado que corresponde y el caso de uso
 * sigue, por ejemplo dejando el reclamo con la asignación pendiente.
 */
public class ServicioExternoNoDisponibleException extends RuntimeException {

    public ServicioExternoNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ServicioExternoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
