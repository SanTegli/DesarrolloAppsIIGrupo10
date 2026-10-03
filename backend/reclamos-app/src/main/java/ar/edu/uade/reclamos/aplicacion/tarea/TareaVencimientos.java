package ar.edu.uade.reclamos.aplicacion.tarea;

import ar.edu.uade.reclamos.aplicacion.servicio.ServicioVencimientos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Ejecuta el control de vencimientos cada cierto intervalo. Se apaga con
 * {@code reclamos.vencimientos.habilitado=false}, que es el valor del perfil test.
 * La lógica está en {@link ServicioVencimientos}; esta clase solo define cuándo corre.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "reclamos.vencimientos", name = "habilitado", havingValue = "true",
        matchIfMissing = true)
public class TareaVencimientos {
    private static final Logger LOGGER = LoggerFactory.getLogger(TareaVencimientos.class);
    private final ServicioVencimientos vencimientos;

    public TareaVencimientos(ServicioVencimientos vencimientos) {
        this.vencimientos = vencimientos;
    }

    @Scheduled(initialDelayString = "${reclamos.vencimientos.intervalo-ms:60000}",
            fixedDelayString = "${reclamos.vencimientos.intervalo-ms:60000}")
    public void ejecutar() {
        int marcados = vencimientos.marcarVencidos();
        if (marcados > 0) {
            LOGGER.info("Control de vencimientos: {} reclamo(s) marcados como vencidos", marcados);
        }
    }
}
