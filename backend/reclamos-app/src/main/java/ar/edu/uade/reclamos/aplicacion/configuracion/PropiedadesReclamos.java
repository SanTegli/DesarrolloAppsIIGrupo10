package ar.edu.uade.reclamos.aplicacion.configuracion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración propia de la aplicación, bajo el prefijo {@code reclamos}.
 * Una propiedad desconocida bajo ese prefijo impide el arranque: así un error de tipeo
 * no pasa inadvertido usando el valor por defecto.
 */
@ConfigurationProperties(prefix = "reclamos", ignoreUnknownFields = false)
public record PropiedadesReclamos(@DefaultValue Prioridad prioridad, @DefaultValue Asignacion asignacion,
                                  @DefaultValue Vencimientos vencimientos) {
    public record Prioridad(@DefaultValue("categoria") String estrategia) { }
    public record Asignacion(@DefaultValue("jurisdiccion") String estrategia) { }

    /**
     * Control de vencimientos. {@code TareaVencimientos} lee las mismas propiedades:
     * {@code reclamos.vencimientos.habilitado} y {@code reclamos.vencimientos.intervalo-ms}.
     */
    public record Vencimientos(@DefaultValue("true") boolean habilitado,
                               @DefaultValue("60000") long intervaloMs) {
        public Vencimientos {
            if (intervaloMs <= 0) {
                throw new IllegalArgumentException("reclamos.vencimientos.intervalo-ms debe ser mayor que cero");
            }
        }
    }
}
