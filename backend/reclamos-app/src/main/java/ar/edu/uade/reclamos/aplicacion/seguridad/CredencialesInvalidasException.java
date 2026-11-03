package ar.edu.uade.reclamos.aplicacion.seguridad;

/**
 * El login falló. El mensaje es el mismo si el email no existe, si la contraseña está mal o si la cuenta está
 * desactivada: así no se puede averiguar qué cuentas existen probando emails.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("El correo o la contraseña no son correctos.");
    }
}
