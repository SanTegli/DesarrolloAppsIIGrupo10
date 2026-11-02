package ar.edu.uade.reclamos.notificaciones.configuracion;

import ar.edu.uade.reclamos.notificaciones.aviso.CorreoSmtp;
import ar.edu.uade.reclamos.notificaciones.aviso.EnvioCorreo;
import ar.edu.uade.reclamos.notificaciones.aviso.LectorMensajes;
import ar.edu.uade.reclamos.notificaciones.aviso.ProcesadorAvisos;
import ar.edu.uade.reclamos.notificaciones.aviso.RegistroProcesados;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionAvisos {

    @Bean
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public LectorMensajes lectorMensajes(ObjectMapper json) {
        return new LectorMensajes(json);
    }

    @Bean
    public RegistroProcesados registroProcesados(JdbcTemplate jdbc) {
        return new RegistroProcesados(jdbc);
    }

    @Bean
    public EnvioCorreo envioCorreo(JavaMailSender correo, @Value("${notificaciones.remitente}") String remitente) {
        return new CorreoSmtp(correo, remitente);
    }

    @Bean
    public ProcesadorAvisos procesadorAvisos(LectorMensajes lector, RegistroProcesados registro, EnvioCorreo correo,
                                             Clock reloj) {
        return new ProcesadorAvisos(lector, registro, correo, reloj);
    }
}
