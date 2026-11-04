package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.time.LocalDateTime;

/**
 * Vecino que presenta reclamos y sigue su estado. Para crear o reabrir reclamos necesita un teléfono
 * verificado: un número, una cuenta.
 */
public class Ciudadano extends Usuario {

    /** Teléfono confirmado desde Telegram, en formato internacional. Nulo hasta que lo verifica. */
    private String telefonoVerificado;
    private LocalDateTime fechaVerificacionTelefono;

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

    /**
     * Guarda el teléfono como verificado. Si ya tenía otro, lo reemplaza: el anterior queda libre para otra
     * cuenta. Que el número no esté en otra cuenta lo controla quien llama, con el repositorio.
     */
    public void verificarTelefono(String telefono, LocalDateTime fecha) {
        this.telefonoVerificado = Telefono.normalizar(telefono);
        this.fechaVerificacionTelefono = Validador.requerirNoNulo(fecha, "fecha de verificación");
    }

    public boolean tieneTelefonoVerificado() {
        return telefonoVerificado != null;
    }

    public String getTelefonoVerificado() {
        return telefonoVerificado;
    }

    public LocalDateTime getFechaVerificacionTelefono() {
        return fechaVerificacionTelefono;
    }
}
