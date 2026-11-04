package ar.edu.uade.reclamos.aplicacion.configuracion;

import java.time.Duration;
import org.springframework.util.unit.DataSize;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración propia de la aplicación, bajo el prefijo {@code reclamos}.
 * Una propiedad desconocida bajo ese prefijo impide el arranque: así un error de tipeo
 * no pasa inadvertido usando el valor por defecto.
 */
@ConfigurationProperties(prefix = "reclamos", ignoreUnknownFields = false)
public record PropiedadesReclamos(@DefaultValue Prioridad prioridad, @DefaultValue Asignacion asignacion,
                                  @DefaultValue Vencimientos vencimientos, @DefaultValue Cobertura cobertura,
                                  @DefaultValue Geocodificacion geocodificacion,
                                  @DefaultValue Mensajeria mensajeria, @DefaultValue Uso uso,
                                  @DefaultValue Reaperturas reaperturas, @DefaultValue Telegram telegram,
                                  @DefaultValue Fotos fotos) {
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
     * Geocodificación de la dirección del reclamo (API externa).
     *
     * @param proveedor      por ahora solo {@code georef}, la API oficial de normalización de direcciones
     * @param url            URL base de la API, sin barra final
     * @param maxSugerencias cuántas sugerencias con coordenadas se le muestran al ciudadano como máximo
     * @param cache          cuánto se recuerda una consulta repetida y cuántas se guardan
     * @param recuadros      recuadro de coordenadas de cada municipio, por nombre; una ubicación elegida
     *                       fuera del recuadro se rechaza. Sin recuadro, solo se validan los rangos
     */
    public record Geocodificacion(@DefaultValue("georef") String proveedor,
                                  @DefaultValue("https://apis.datos.gob.ar/georef/api/v2.0") String url,
                                  @DefaultValue("1s") Duration tiempoConexion,
                                  @DefaultValue("3s") Duration tiempoRespuesta,
                                  @DefaultValue("5") int maxSugerencias,
                                  @DefaultValue Cache cache,
                                  Map<String, Recuadro> recuadros) {
        public Geocodificacion {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("reclamos.geocodificacion.url no puede estar vacía");
            }
            requerirPositivo(tiempoConexion, "reclamos.geocodificacion.tiempo-conexion");
            requerirPositivo(tiempoRespuesta, "reclamos.geocodificacion.tiempo-respuesta");
            if (maxSugerencias < 1 || maxSugerencias > 10) {
                throw new IllegalArgumentException("reclamos.geocodificacion.max-sugerencias debe estar entre 1 y 10");
            }
            recuadros = recuadros == null ? Map.of() : Map.copyOf(recuadros);
        }

        /** Recuadro del municipio, buscado por nombre sin distinguir mayúsculas. */
        public Optional<Recuadro> recuadroDe(String municipio) {
            return recuadros.entrySet().stream()
                    .filter(entrada -> entrada.getKey().equalsIgnoreCase(municipio))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }
    }

    /** Consultas repetidas: se responden de memoria durante {@code duracion}, hasta {@code maximo} distintas. */
    public record Cache(@DefaultValue("10m") Duration duracion, @DefaultValue("500") int maximo) {
        public Cache {
            requerirPositivo(duracion, "reclamos.geocodificacion.cache.duracion");
            if (maximo < 1) {
                throw new IllegalArgumentException("reclamos.geocodificacion.cache.maximo debe ser mayor que cero");
            }
        }
    }

    /** Rectángulo de coordenadas que contiene al municipio. Es un filtro grueso, no el límite exacto. */
    public record Recuadro(double latitudMinima, double latitudMaxima, double longitudMinima,
                           double longitudMaxima) {
        public Recuadro {
            if (latitudMinima >= latitudMaxima || longitudMinima >= longitudMaxima) {
                throw new IllegalArgumentException(
                        "reclamos.geocodificacion.recuadros: cada mínimo tiene que ser menor que su máximo");
            }
        }

        public boolean contiene(double latitud, double longitud) {
            return latitud >= latitudMinima && latitud <= latitudMaxima && longitud >= longitudMinima
                    && longitud <= longitudMaxima;
        }
    }

    /**
     * Mensajería con RabbitMQ y outbox. Apagada por defecto: así los tests y el desarrollo sin broker no
     * acumulan mensajes. Docker Compose la enciende.
     *
     * @param intervaloMs        cada cuánto se publican los pendientes de la outbox
     * @param lote               cuántos pendientes se publican por pasada como máximo
     * @param esperaConfirmacion cuánto se espera la confirmación del broker por cada mensaje
     */
    public record Mensajeria(@DefaultValue("false") boolean habilitada,
                             @DefaultValue("2000") long intervaloMs,
                             @DefaultValue("50") int lote,
                             @DefaultValue("5s") Duration esperaConfirmacion) {
        public Mensajeria {
            if (intervaloMs <= 0) {
                throw new IllegalArgumentException("reclamos.mensajeria.intervalo-ms debe ser mayor que cero");
            }
            if (lote < 1) {
                throw new IllegalArgumentException("reclamos.mensajeria.lote debe ser mayor que cero");
            }
            requerirPositivo(esperaConfirmacion, "reclamos.mensajeria.espera-confirmacion");
        }
    }

    /**
     * Límites de uso para los ciudadanos. Suben el costo de abusar; no lo eliminan.
     *
     * @param reclamosPorDia  cuántos reclamos nuevos puede presentar un ciudadano por día calendario
     * @param codigosPorHora  cuántos códigos de verificación del teléfono puede pedir por hora
     * @param vigenciaCodigo  cuánto dura un código de verificación
     */
    public record Uso(@DefaultValue("5") int reclamosPorDia,
                      @DefaultValue("3") int codigosPorHora,
                      @DefaultValue("10m") Duration vigenciaCodigo) {
        public Uso {
            if (reclamosPorDia < 1) {
                throw new IllegalArgumentException("reclamos.uso.reclamos-por-dia debe ser mayor que cero");
            }
            if (codigosPorHora < 1) {
                throw new IllegalArgumentException("reclamos.uso.codigos-por-hora debe ser mayor que cero");
            }
            requerirPositivo(vigenciaCodigo, "reclamos.uso.vigencia-codigo");
        }
    }

    /** Reaperturas: cuántas por reclamo y hasta cuánto después de la última resolución. */
    public record Reaperturas(@DefaultValue("1") int maximo, @DefaultValue("7d") Duration plazo) {
        public Reaperturas {
            if (maximo < 0) {
                throw new IllegalArgumentException("reclamos.reaperturas.maximo no puede ser negativo");
            }
            requerirPositivo(plazo, "reclamos.reaperturas.plazo");
        }
    }

    /**
     * Verificación del teléfono por Telegram.
     *
     * @param modo        {@code simulado} (sin Telegram: un endpoint confirma el código) o {@code telegram}
     * @param token       token del bot que da @BotFather; solo por la variable TELEGRAM_BOT_TOKEN, nunca en Git
     * @param usuarioBot  nombre del bot sin la arroba, para armar el enlace {@code t.me/<bot>?start=<código>}
     * @param intervaloMs cada cuánto se consultan los mensajes nuevos del bot
     */
    public record Telegram(@DefaultValue("simulado") String modo,
                           @DefaultValue("https://api.telegram.org") String url,
                           @DefaultValue("") String token,
                           @DefaultValue("") String usuarioBot,
                           @DefaultValue("2000") long intervaloMs,
                           @DefaultValue("10s") Duration tiempoLimite) {
        public Telegram {
            if (!"simulado".equals(modo) && !"telegram".equals(modo)) {
                throw new IllegalArgumentException("reclamos.telegram.modo debe ser simulado o telegram");
            }
            if ("telegram".equals(modo) && (token == null || token.isBlank() || usuarioBot == null
                    || usuarioBot.isBlank())) {
                throw new IllegalArgumentException("Con reclamos.telegram.modo=telegram hay que definir "
                        + "TELEGRAM_BOT_TOKEN y TELEGRAM_BOT_USUARIO");
            }
            if (intervaloMs <= 0) {
                throw new IllegalArgumentException("reclamos.telegram.intervalo-ms debe ser mayor que cero");
            }
            requerirPositivo(tiempoLimite, "reclamos.telegram.tiempo-limite");
        }

        public boolean simulado() {
            return "simulado".equals(modo);
        }

        /** Sin el token: así el record se puede loguear sin filtrar la clave del bot. */
        @Override
        public String toString() {
            return "Telegram[modo=" + modo + ", usuarioBot=" + usuarioBot + ", token="
                    + (token == null || token.isBlank() ? "sin definir" : "****") + "]";
        }
    }

    /**
     * Foto del reclamo (PR 16).
     *
     * @param directorio   dónde se guardan las fotos; en Compose, el volumen {@code /data/fotos}
     * @param tamanoMaximo peso máximo del archivo subido
     * @param ladoMaximo   ancho y alto máximos en píxeles, para no decodificar imágenes gigantes
     * @param calidad      calidad del JPEG re-codificado, entre 0 y 1
     */
    public record Fotos(@DefaultValue("datos/fotos") String directorio,
                        @DefaultValue("5MB") DataSize tamanoMaximo,
                        @DefaultValue("4000") int ladoMaximo,
                        @DefaultValue("0.85") float calidad) {
        public Fotos {
            if (directorio == null || directorio.isBlank()) {
                throw new IllegalArgumentException("reclamos.fotos.directorio no puede estar vacío");
            }
            if (tamanoMaximo == null || tamanoMaximo.toBytes() <= 0) {
                throw new IllegalArgumentException("reclamos.fotos.tamano-maximo debe ser mayor que cero");
            }
            if (ladoMaximo < 1) {
                throw new IllegalArgumentException("reclamos.fotos.lado-maximo debe ser mayor que cero");
            }
            if (calidad <= 0 || calidad > 1) {
                throw new IllegalArgumentException("reclamos.fotos.calidad debe estar entre 0 y 1");
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
