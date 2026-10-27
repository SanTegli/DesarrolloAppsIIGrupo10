package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.validacion.Validador;
import java.util.Objects;

/**
 * Dónde ocurre el reclamo según un geocodificador. Es un valor inmutable: para cambiarla se registra
 * otra. Sin coordenadas confirmadas queda {@link EstadoUbicacion#PENDIENTE} y nunca se inventan.
 */
public final class Ubicacion {
    public static final int LONGITUD_MAXIMA_DIRECCION = 300;
    public static final int LONGITUD_MAXIMA_PROVEEDOR = 40;

    private EstadoUbicacion estado;
    private Double latitud;
    private Double longitud;
    private String direccionNormalizada;
    private String proveedor;

    /** Requerido por JPA. */
    protected Ubicacion() {
    }

    private Ubicacion(EstadoUbicacion estado, Double latitud, Double longitud, String direccionNormalizada,
                      String proveedor) {
        this.estado = estado;
        this.latitud = latitud;
        this.longitud = longitud;
        this.direccionNormalizada = direccionNormalizada;
        this.proveedor = proveedor;
    }

    /** Sin coordenadas: solo se conoce la dirección escrita por el ciudadano. */
    public static Ubicacion pendiente() {
        return new Ubicacion(EstadoUbicacion.PENDIENTE, null, null, null, null);
    }

    /**
     * Coordenadas elegidas entre las sugerencias de un geocodificador.
     *
     * @param proveedor quién geocodificó, por ejemplo {@code georef}
     */
    public static Ubicacion confirmada(double latitud, double longitud, String direccionNormalizada,
                                       String proveedor) {
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new DatosInvalidosException("La latitud debe estar entre -90 y 90.");
        }
        if (!Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new DatosInvalidosException("La longitud debe estar entre -180 y 180.");
        }
        return new Ubicacion(EstadoUbicacion.CONFIRMADA, latitud, longitud,
                Validador.requerirTexto(direccionNormalizada, "direccionNormalizada", LONGITUD_MAXIMA_DIRECCION),
                Validador.requerirTexto(proveedor, "proveedor", LONGITUD_MAXIMA_PROVEEDOR));
    }

    public boolean isConfirmada() {
        return estado == EstadoUbicacion.CONFIRMADA;
    }

    public EstadoUbicacion getEstado() {
        return estado;
    }

    /** {@code null} mientras la ubicación está pendiente. */
    public Double getLatitud() {
        return latitud;
    }

    /** {@code null} mientras la ubicación está pendiente. */
    public Double getLongitud() {
        return longitud;
    }

    /** La dirección tal como la interpretó el geocodificador; {@code null} si está pendiente. */
    public String getDireccionNormalizada() {
        return direccionNormalizada;
    }

    /** {@code null} si está pendiente. */
    public String getProveedor() {
        return proveedor;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Ubicacion otra && estado == otra.estado && Objects.equals(latitud, otra.latitud)
                && Objects.equals(longitud, otra.longitud)
                && Objects.equals(direccionNormalizada, otra.direccionNormalizada)
                && Objects.equals(proveedor, otra.proveedor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(estado, latitud, longitud, direccionNormalizada, proveedor);
    }
}
