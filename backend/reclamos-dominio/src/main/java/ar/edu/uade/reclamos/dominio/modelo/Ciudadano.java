package ar.edu.uade.reclamos.dominio.modelo;

/** Vecino que presenta reclamos y sigue su estado. */
public class Ciudadano extends Usuario {

    /** Requerido por JPA. */
    protected Ciudadano() {
    }

    public Ciudadano(String dni, String nombre, String apellido, String email, String telefono) {
        super(dni, nombre, apellido, email, telefono);
    }

    @Override
    public Rol getRol() {
        return Rol.CIUDADANO;
    }
}
