package ar.edu.uade.reclamos.dominio.modelo;

/** Gestiona el sistema: asigna, reasigna y rechaza reclamos. */
public class Administrador extends Usuario {

    /** Requerido por JPA. */
    protected Administrador() {
    }

    public Administrador(String dni, String nombre, String apellido, String email, String telefono) {
        super(dni, nombre, apellido, email, telefono);
    }

    @Override
    public Rol getRol() {
        return Rol.ADMINISTRADOR;
    }
}
