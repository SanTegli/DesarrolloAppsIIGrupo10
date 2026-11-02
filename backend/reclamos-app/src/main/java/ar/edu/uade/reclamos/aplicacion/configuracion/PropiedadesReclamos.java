package ar.edu.uade.reclamos.aplicacion.configuracion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración propia de la aplicación, bajo el prefijo {@code reclamos}.
 * Una propiedad desconocida bajo ese prefijo impide el arranque: así un error de tipeo
 * no pasa inadvertido usando el valor por defecto.
 */
@ConfigurationProperties(prefix = "reclamos", ignoreUnknownFields = false)
public record PropiedadesReclamos(@DefaultValue Prioridad prioridad, @DefaultValue Asignacion asignacion,
                                  @DefaultValue Vencimientos vencimientos, @DefaultValue Cobertura cobertura) {
    public record Prioridad(@DefaultValue("categoria") String estrategia) { }
    public record Asignacion(@DefaultValue("jurisdiccion") String estrategia) { }

    /**
     * De dónde salen las áreas candidatas: {@code local} (la base del backend) o {@code soap} (el servicio de
     * cobertura en {@code url}). Si el servicio no contesta dentro de {@code tiempoLimite}, el reclamo queda
     * con la asignación pendiente.
     */
    public record Cobertura(@DefaultValue("local") String origen,
                            @DefaultValue("http://localhost:8081/ws") String url,
                            @DefaultValue("2s") Duration tiempoLimite) {
        public Cobertura {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("reclamos.cobertura.url no puede estar vacía");
            }
            if (tiempoLimite == null || tiempoLimite.isNegative() || tiempoLimite.isZero()) {
                throw new IllegalArgumentException("reclamos.cobertura.tiempo-limite debe ser mayor que cero");
            }
        }
    }

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
