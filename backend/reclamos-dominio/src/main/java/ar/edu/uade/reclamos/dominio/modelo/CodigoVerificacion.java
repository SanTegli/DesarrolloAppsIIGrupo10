package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.time.LocalDateTime;

/**
 * Código de un solo uso para verificar el teléfono de un ciudadano. Vence a los pocos minutos y, una vez
 * usado, no sirve más.
 */
public class CodigoVerificacion extends Entidad {

    public static final int LONGITUD = 8;

    private String codigo;
    private Ciudadano ciudadano;
    private LocalDateTime fechaCreacion;
    private LocalDateTime vence;
    private LocalDateTime fechaUso;

    /** Requerido por JPA. */
    protected CodigoVerificacion() {
    }

    public CodigoVerificacion(String codigo, Ciudadano ciudadano, LocalDateTime fechaCreacion, LocalDateTime vence) {
        this.codigo = Validador.requerirTexto(codigo, "código", LONGITUD);
        this.ciudadano = Validador.requerirNoNulo(ciudadano, "ciudadano");
        this.fechaCreacion = Validador.requerirNoNulo(fechaCreacion, "fechaCreacion");
        this.vence = Validador.requerirNoNulo(vence, "vence");
        if (!vence.isAfter(fechaCreacion)) {
            throw new ReglaNegocioException("El código tiene que vencer después de crearse.");
        }
    }

    public boolean estaVigente(LocalDateTime ahora) {
        return fechaUso == null && !ahora.isAfter(vence);
    }

    /** Lo marca como usado. Un código vencido o ya usado no se puede usar. */
    public void usar(LocalDateTime ahora) {
        if (fechaUso != null) {
            throw new ReglaNegocioException("El código ya se usó. Pedí uno nuevo.");
        }
        if (ahora.isAfter(vence)) {
            throw new ReglaNegocioException("El código venció. Pedí uno nuevo.");
        }
        this.fechaUso = ahora;
    }

    public boolean perteneceA(Usuario usuario) {
        return ciudadano.equals(usuario);
    }

    public String getCodigo() {
        return codigo;
    }

    public Ciudadano getCiudadano() {
        return ciudadano;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getVence() {
        return vence;
    }

    public LocalDateTime getFechaUso() {
        return fechaUso;
    }
}
