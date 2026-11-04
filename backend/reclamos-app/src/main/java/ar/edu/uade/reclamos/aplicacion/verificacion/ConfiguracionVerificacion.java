package ar.edu.uade.reclamos.aplicacion.verificacion;

import ar.edu.uade.reclamos.aplicacion.configuracion.PropiedadesReclamos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Elige el canal de verificación por {@code reclamos.telegram.modo}. Con {@code telegram} además arranca el bot,
 * que consulta los mensajes cada {@code reclamos.telegram.intervalo-ms}.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionVerificacion {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguracionVerificacion.class);

    @Bean
    public CanalVerificacion canalVerificacion(PropiedadesReclamos propiedades) {
        PropiedadesReclamos.Telegram telegram = propiedades.telegram();
        LOGGER.info("Verificación del teléfono: {}", telegram);
        return telegram.simulado() ? new CanalSimulado() : new CanalTelegram(telegram.usuarioBot());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "reclamos.telegram.modo", havingValue = "telegram")
    static class ConTelegram {

        @Bean
        public ClienteTelegram clienteTelegram(PropiedadesReclamos propiedades) {
            PropiedadesReclamos.Telegram telegram = propiedades.telegram();
            return ClienteTelegram.crear(telegram.url(), telegram.token(), telegram.tiempoLimite());
        }

        @Bean
        public BotTelegram botTelegram(ClienteTelegram telegram, ServicioVerificacionTelefono verificacion) {
            return new BotTelegram(telegram, verificacion);
        }
    }
}
