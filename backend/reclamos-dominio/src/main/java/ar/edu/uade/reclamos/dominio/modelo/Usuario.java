package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;

/** Persona que usa el sistema. El rol lo define la subclase. */
public abstract class Usuario extends Entidad {

    private String dni;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private boolean activo = true;

    /** Requerido por JPA. */
    protected Usuario() {
    }

    protected Usuario(String dni, String nombre, String apellido, String email, String telefono) {
        this.dni = Validador.requerirDni(dni);
        this.nombre = Validador.requerirTexto(nombre, "nombre", 80);
        this.apellido = Validador.requerirTexto(apellido, "apellido", 80);
        this.email = Validador.requerirEmail(email);
        this.telefono = telefono;
    }

    public abstract Rol getRol();

    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    public String getDni() {
        return dni;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public boolean isActivo() {
        return activo;
    }
}
