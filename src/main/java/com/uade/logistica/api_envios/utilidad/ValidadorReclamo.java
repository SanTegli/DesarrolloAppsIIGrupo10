package com.uade.logistica.api_envios.utilidad;

public class ValidadorReclamo {
    public static boolean validarTexto(String texto) {
        return texto != null && !texto.trim().isEmpty();
    }

    public static boolean validarDni(String dni) {
        return dni != null && dni.matches("\\d{7,8}");
    }
}