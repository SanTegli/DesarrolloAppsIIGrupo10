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
    /**
     * Estrategia de prioridad: {@code categoria}, {@code palabras-clave} o {@code ia}. Con {@code ia} se usa
     * el servicio de prioridad (REST 2) en {@code ia.url}, con respaldo por categoría.
     */
    public record Prioridad(@DefaultValue("categoria") String estrategia, @DefaultValue Ia ia) { }

    /**
     * Cliente del servicio de prioridad con IA.
     *
     * @param tiempoConexion  cuánto se espera para conectar
     * @param tiempoRespuesta cuánto se espera la respuesta; el modelo puede tardar unos segundos
     * @param circuito        cuándo dejar de llamar al servicio si falla seguido
     */
    public record Ia(@DefaultValue("http://localhost:8082") String url,
                     @DefaultValue("1s") Duration tiempoConexion,
                     @DefaultValue("6s") Duration tiempoRespuesta,
                     @DefaultValue Circuito circuito) {
        public Ia {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("reclamos.prioridad.ia.url no puede estar vacía");
            }
            requerirPositivo(tiempoConexion, "reclamos.prioridad.ia.tiempo-conexion");
            requerirPositivo(tiempoRespuesta, "reclamos.prioridad.ia.tiempo-respuesta");
        }
    }

    /**
     * Circuit breaker del cliente de IA: mira las últimas {@code llamadas}; si falló la mitad, no vuelve a
     * llamar durante {@code espera}.
     */
    public record Circuito(@DefaultValue("5") int llamadas, @DefaultValue("30s") Duration espera) {
        public Circuito {
            if (llamadas < 1) {
                throw new IllegalArgumentException("reclamos.prioridad.ia.circuito.llamadas debe ser mayor que cero");
            }
            requerirPositivo(espera, "reclamos.prioridad.ia.circuito.espera");
        }
    }

    private static void requerirPositivo(Duration duracion, String propiedad) {
        if (duracion == null || duracion.isZero() || duracion.isNegative()) {
            throw new IllegalArgumentException(propiedad + " debe ser mayor que cero");
        }
    }

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
