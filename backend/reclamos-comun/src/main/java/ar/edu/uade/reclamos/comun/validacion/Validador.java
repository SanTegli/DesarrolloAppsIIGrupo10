package ar.edu.uade.reclamos.comun.validacion;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;

/**
 * Validaciones de formato reutilizables por cualquier módulo.
 * Los métodos {@code requerir...} lanzan {@link DatosInvalidosException} y devuelven el valor validado.
 */
public final class Validador {

    private static final String PATRON_DNI = "\\d{7,8}";
    private static final String PATRON_EMAIL = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    private Validador() {
    }

    public static boolean esTextoValido(String texto) {
        return texto != null && !texto.isBlank();
    }

    public static boolean esDniValido(String dni) {
        return dni != null && dni.matches(PATRON_DNI);
    }

    public static boolean esEmailValido(String email) {
        return email != null && email.matches(PATRON_EMAIL);
    }

    public static <T> T requerirNoNulo(T valor, String campo) {
        if (valor == null) {
            throw new DatosInvalidosException("El campo '" + campo + "' es obligatorio.");
        }
        return valor;
    }

    /** Devuelve el texto sin espacios al inicio ni al final. */
    public static String requerirTexto(String texto, String campo) {
        if (!esTextoValido(texto)) {
            throw new DatosInvalidosException("El campo '" + campo + "' no puede estar vacío.");
        }
        return texto.trim();
    }

    public static String requerirTexto(String texto, String campo, int longitudMaxima) {
        String limpio = requerirTexto(texto, campo);
        if (limpio.length() > longitudMaxima) {
            throw new DatosInvalidosException(
                    "El campo '" + campo + "' no puede superar los " + longitudMaxima + " caracteres.");
        }
        return limpio;
    }

    public static int requerirPositivo(int valor, String campo) {
        if (valor <= 0) {
            throw new DatosInvalidosException("El campo '" + campo + "' debe ser mayor que cero.");
        }
        return valor;
    }

    public static String requerirDni(String dni) {
        if (!esDniValido(dni)) {
            throw new DatosInvalidosException("DNI inválido: debe tener 7 u 8 dígitos.");
        }
        return dni;
    }

    public static String requerirEmail(String email) {
        if (!esEmailValido(email)) {
            throw new DatosInvalidosException("El formato del correo electrónico es inválido.");
        }
        return email;
    }
}
