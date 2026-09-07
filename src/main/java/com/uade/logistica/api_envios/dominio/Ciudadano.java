package com.uade.logistica.api_envios.dominio;

public class Ciudadano {
    private String dni;
    private String nombre;
    private String email;

    public Ciudadano(String dni, String nombre, String email) {
        this.dni = dni;
        this.nombre = nombre;
        this.email = email;
    }

    public String getDni() { return dni; }
    public String getNombre() { return nombre; }
    public String getEmail() { return email; }
}