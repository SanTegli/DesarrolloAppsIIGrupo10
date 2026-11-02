package ar.edu.uade.reclamos.prioridad.configuracion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del servicio bajo {@code prioridad.ia}. Las claves llegan por variables de entorno
 * ({@code GEMINI_API_KEY}, {@code OPENAI_API_KEY}) y nunca se escriben en el código ni en Git.
 *
 * @param proveedor     {@code gemini}, {@code openai} o {@code simulado}
 * @param tiempoLimite  cuánto se espera al proveedor, para conectar y para leer la respuesta
 * @param instrucciones archivo con los criterios de prioridad que recibe el modelo
 */
@ConfigurationProperties(prefix = "prioridad.ia", ignoreUnknownFields = false)
public record PropiedadesIA(@DefaultValue("gemini") String proveedor,
                            @DefaultValue("5s") Duration tiempoLimite,
                            @DefaultValue("classpath:instrucciones-prioridad.txt") String instrucciones,
                            @DefaultValue Proveedor gemini,
                            @DefaultValue Proveedor openai) {

    public PropiedadesIA {
        if (tiempoLimite == null || tiempoLimite.isZero() || tiempoLimite.isNegative()) {
            throw new IllegalArgumentException("prioridad.ia.tiempo-limite debe ser mayor que cero");
        }
    }

    /** URL base, clave y modelo de un proveedor. {@link #toString()} no muestra la clave. */
    public record Proveedor(String url, String clave, String modelo) {
        public boolean tieneClave() {
            return clave != null && !clave.isBlank();
        }

        @Override
        public String toString() {
            return "Proveedor[url=" + url + ", modelo=" + modelo + ", clave=" + (tieneClave() ? "****" : "sin definir")
                    + "]";
        }
    }
}
