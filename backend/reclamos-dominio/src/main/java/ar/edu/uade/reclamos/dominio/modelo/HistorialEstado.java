package ar.edu.uade.reclamos.dominio.modelo;

import java.time.LocalDateTime;

/** Registro inmutable de un cambio de estado del reclamo. Lo crea únicamente {@link Reclamo}. */
public class HistorialEstado extends Entidad {

    private Reclamo reclamo;
    private EstadoReclamo estadoAnterior;
    private EstadoReclamo estadoNuevo;
    private LocalDateTime fecha;
    private String observacion;
    private Usuario usuario;

    /** Requerido por JPA. */
    protected HistorialEstado() {
    }

    HistorialEstado(Reclamo reclamo, EstadoReclamo estadoAnterior, EstadoReclamo estadoNuevo,
                    Usuario usuario, String observacion, LocalDateTime fecha) {
        this.reclamo = reclamo;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuario = usuario;
        this.observacion = observacion;
        this.fecha = fecha;
    }

    /** Verdadero si el cambio lo hizo el sistema y no una persona. */
    public boolean fueAutomatico() {
        return usuario == null;
    }

    public Reclamo getReclamo() {
        return reclamo;
    }

    /** {@code null} en el primer registro, cuando el reclamo se crea. */
    public EstadoReclamo getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoReclamo getEstadoNuevo() {
        return estadoNuevo;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public String getObservacion() {
        return observacion;
    }

    /** {@code null} cuando el cambio fue automático. */
    public Usuario getUsuario() {
        return usuario;
    }
}
