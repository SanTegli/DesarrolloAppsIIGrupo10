package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.time.LocalDateTime;

/**
 * Un ciudadano dice "a mí también me pasa" sobre el reclamo de otro, en lugar de crear uno repetido.
 *
 * <p>Apoyar no da permisos sobre el reclamo (no lo puede ver completo ni cambiarle el estado) ni cambia su
 * prioridad: solo suma al contador que ven el autor y el municipio. Un ciudadano apoya cada reclamo una sola
 * vez; lo asegura una restricción única en la base.
 */
public class Apoyo extends Entidad {

    private Ciudadano ciudadano;
    private Reclamo reclamo;
    private LocalDateTime fecha;

    /** Requerido por JPA. */
    protected Apoyo() {
    }

    public Apoyo(Ciudadano ciudadano, Reclamo reclamo, LocalDateTime fecha) {
        this.ciudadano = Validador.requerirNoNulo(ciudadano, "ciudadano");
        this.reclamo = Validador.requerirNoNulo(reclamo, "reclamo");
        this.fecha = Validador.requerirNoNulo(fecha, "fecha");
        if (reclamo.perteneceA(ciudadano)) {
            throw new ReglaNegocioException("No podés apoyar tu propio reclamo.");
        }
        if (!reclamo.getEstado().estaPendienteDeResolucion()) {
            throw new ReglaNegocioException("El reclamo " + reclamo.getNumero()
                    + " ya no está abierto: solo se apoyan reclamos que siguen pendientes.");
        }
    }

    public Ciudadano getCiudadano() {
        return ciudadano;
    }

    public Reclamo getReclamo() {
        return reclamo;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
