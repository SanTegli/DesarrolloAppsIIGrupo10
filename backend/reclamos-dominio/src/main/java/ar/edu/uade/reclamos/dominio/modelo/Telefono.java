package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;

/**
 * Teléfonos en formato internacional ({@code +5491155550000}). Telegram los manda con o sin el {@code +}; acá
 * quedan siempre igual, así la regla "un número, una cuenta" compara bien.
 */
public final class Telefono {

    private static final int MINIMO_DIGITOS = 8;
    private static final int MAXIMO_DIGITOS = 15;

    private Telefono() {
    }

    /** {@code "+54 9 11 5555-0000"} o {@code "5491155550000"} → {@code "+5491155550000"}. */
    public static String normalizar(String telefono) {
        String digitos = telefono == null ? "" : telefono.replaceAll("[\\s\\-().+]", "");
        if (!digitos.matches("[1-9]\\d{" + (MINIMO_DIGITOS - 1) + "," + (MAXIMO_DIGITOS - 1) + "}")) {
            throw new DatosInvalidosException("El teléfono tiene que tener entre " + MINIMO_DIGITOS + " y "
                    + MAXIMO_DIGITOS + " dígitos, con el código de país.");
        }
        return "+" + digitos;
    }

    /**
     * Para mostrarlo: el código de país y los últimos cuatro dígitos ({@code +54 ••••••••1234}). El número
     * completo no sale del backend.
     */
    public static String enmascarar(String telefono) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replace("+", "");
        int visibles = 4;
        String pais = digitos.substring(0, Math.min(2, digitos.length()));
        int ocultos = Math.max(0, digitos.length() - pais.length() - visibles);
        return "+" + pais + " " + "•".repeat(ocultos) + digitos.substring(digitos.length() - Math.min(visibles,
                digitos.length() - pais.length()));
    }
}
