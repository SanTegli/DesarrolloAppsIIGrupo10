package com.uade.logistica.api_envios.utilidad;

public class ValidadorReclamo {

    private ValidadorReclamo() {
        // Clase de utilidades, no instanciable
    }

    public static boolean validarTexto(String texto) {
        return texto != null && !texto.trim().isEmpty();
    }

    public static boolean validarDni(String dni) {
        return dni != null && dni.matches("\\d{7,8}");
    }

    public static boolean validarEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    public static void requerirDniValido(String dni) {
        if (!validarDni(dni)) {
            throw new IllegalArgumentException("DNI de ciudadano inválido. Debe contener entre 7 y 8 dígitos numéricos.");
        }
    }

    public static void requerirTextoNoVacio(String texto, String nombreCampo) {
        if (!validarTexto(texto)) {
            throw new IllegalArgumentException("El campo '" + nombreCampo + "' no puede ser nulo ni estar vacío.");
        }
    }

    public static void requerirEmailValido(String email) {
        if (!validarEmail(email)) {
            throw new IllegalArgumentException("El formato del correo electrónico es inválido.");
        }
    }
}