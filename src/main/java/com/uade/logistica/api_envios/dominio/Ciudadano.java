package com.uade.logistica.api_envios.dominio;

import com.uade.logistica.api_envios.utilidad.ValidadorReclamo;
import java.util.Objects;

public class Ciudadano {
    private final String dni;
    private final String nombre;
    private final String email;

    public Ciudadano(String dni, String nombre, String email) {
        ValidadorReclamo.requerirDniValido(dni);
        ValidadorReclamo.requerirTextoNoVacio(nombre, "nombre");
        ValidadorReclamo.requerirEmailValido(email);

        this.dni = dni;
        this.nombre = nombre;
        this.email = email;
    }

    public String getDni() { return dni; }
    public String getNombre() { return nombre; }
    public String getEmail() { return email; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ciudadano ciudadano = (Ciudadano) o;
        return Objects.equals(dni, ciudadano.dni);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dni);
    }

    @Override
    public String toString() {
        return "Ciudadano{dni='" + dni + "', nombre='" + nombre + "', email='" + email + "'}";
    }
}