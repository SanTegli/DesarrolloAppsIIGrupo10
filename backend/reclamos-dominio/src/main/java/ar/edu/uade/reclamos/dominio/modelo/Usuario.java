package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.validacion.Validador;

/** Persona que usa el sistema. El rol lo define la subclase. */
public abstract class Usuario extends Entidad {

    private String dni;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private boolean activo = true;
    /** Hash de la contraseña (BCrypt). El dominio no sabe cómo se calcula: lo recibe hecho. Nulo si no puede entrar. */
    private String hashContrasena;

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

    /** Define la contraseña con su hash ya calculado. Nunca se guarda la contraseña en texto. */
    public void definirContrasena(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new DatosInvalidosException("La contraseña no puede quedar vacía.");
        }
        this.hashContrasena = hash;
    }

    public boolean tieneContrasena() {
        return hashContrasena != null;
    }

    public String getHashContrasena() {
        return hashContrasena;
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
