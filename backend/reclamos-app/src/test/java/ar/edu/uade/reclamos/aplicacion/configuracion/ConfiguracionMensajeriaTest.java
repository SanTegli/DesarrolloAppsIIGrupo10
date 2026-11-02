package ar.edu.uade.reclamos.aplicacion.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerRabbit;
import ar.edu.uade.reclamos.aplicacion.mensajeria.OutboxAvisos;
import ar.edu.uade.reclamos.aplicacion.mensajeria.PublicadorMensajes;
import ar.edu.uade.reclamos.aplicacion.mensajeria.SalidaDeAvisos;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfiguracionMensajeriaTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionAplicacion.class, ConfiguracionMensajeria.class)
            .withBean(ReclamoRepository.class, () -> mock(ReclamoRepository.class))
            .withBean(AreaMunicipalRepository.class, () -> mock(AreaMunicipalRepository.class))
            .withBean(MensajeSalienteRepository.class, () -> mock(MensajeSalienteRepository.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class));

    @Test
    void apagadaPorDefectoLosAvisosQuedanSoloInternos() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(PublicadorMensajes.class)
                    .doesNotHaveBean(TopicExchange.class);
            assertThat(ctx.getBean(SalidaDeAvisos.class)).isSameAs(SalidaDeAvisos.NINGUNA);
        });
    }

    @Test
    void encendidaUsaLaOutboxElPublicadorYDeclaraElExchange() {
        contexto.withPropertyValues("reclamos.mensajeria.habilitada=true", "reclamos.mensajeria.lote=10",
                "reclamos.mensajeria.espera-confirmacion=3s").run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(PublicadorMensajes.class);
            assertThat(ctx.getBean(SalidaDeAvisos.class)).isInstanceOf(OutboxAvisos.class);
            TopicExchange exchange = ctx.getBean(TopicExchange.class);
            assertThat(exchange.getName()).isEqualTo(BrokerRabbit.EXCHANGE);
            assertThat(exchange.isDurable()).isTrue();
            PropiedadesReclamos.Mensajeria mensajeria = ctx.getBean(PropiedadesReclamos.class).mensajeria();
            assertThat(mensajeria.lote()).isEqualTo(10);
            assertThat(mensajeria.esperaConfirmacion()).isEqualTo(Duration.ofSeconds(3));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"reclamos.mensajeria.intervalo-ms=0", "reclamos.mensajeria.lote=0",
            "reclamos.mensajeria.espera-confirmacion=0s"})
    void configuracionInvalidaImpideArrancar(String propiedad) {
        contexto.withPropertyValues(propiedad).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .hasStackTraceContaining(propiedad.substring(0, propiedad.indexOf('=')));
        });
    }
}
