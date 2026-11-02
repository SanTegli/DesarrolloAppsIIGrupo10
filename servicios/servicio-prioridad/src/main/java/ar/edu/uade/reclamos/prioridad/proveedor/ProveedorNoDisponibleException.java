package ar.edu.uade.reclamos.prioridad.proveedor;

/** El proveedor de IA no se puede usar: falta la clave, la rechazó, se agotó la cuota o devolvió un error. Se responde 503. */
public class ProveedorNoDisponibleException extends RuntimeException {

    public ProveedorNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ProveedorNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
