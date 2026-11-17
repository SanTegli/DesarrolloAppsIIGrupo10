package ar.edu.uade.reclamos.aplicacion.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ar.edu.uade.reclamos.aplicacion.mensajeria.BrokerRabbit;
import ar.edu.uade.reclamos.aplicacion.mensajeria.OutboxAvisos;
import ar.edu.uade.reclamos.aplicacion.mensajeria.PublicadorMensajes;
import ar.edu.uade.reclamos.aplicacion.mensajeria.SalidaDeAvisos;
import ar.edu.uade.reclamos.aplicacion.prioridad.ListenerReevaluacionPrioridad;
import ar.edu.uade.reclamos.aplicacion.prioridad.OutboxReevaluaciones;
import ar.edu.uade.reclamos.aplicacion.prioridad.ServicioReevaluacionPrioridad;
import ar.edu.uade.reclamos.aplicacion.prioridad.SolicitudesReevaluacion;
import ar.edu.uade.reclamos.aplicacion.prioridad.TopologiaReevaluacion;
import ar.edu.uade.reclamos.dominio.evento.PublicadorEventos;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.MensajeSalienteRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfiguracionMensajeriaTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionAplicacion.class, ConfiguracionMensajeria.class)
            .withBean(ReclamoRepository.class, () -> mock(ReclamoRepository.class))
            .withBean(CategoriaRepository.class, () -> mock(CategoriaRepository.class))
            .withBean(AreaMunicipalRepository.class, () -> mock(AreaMunicipalRepository.class))
            .withBean(MensajeSalienteRepository.class, () -> mock(MensajeSalienteRepository.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean(PublicadorEventos.class, () -> mock(PublicadorEventos.class));

    @Test
    void apagadaPorDefectoLosAvisosQuedanSoloInternos() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(PublicadorMensajes.class)
                    .doesNotHaveBean(TopicExchange.class);
            assertThat(ctx.getBean(SalidaDeAvisos.class)).isSameAs(SalidaDeAvisos.NINGUNA);
            assertThat(ctx.getBean(SolicitudesReevaluacion.class)).isSameAs(SolicitudesReevaluacion.NINGUNA);
            assertThat(ctx).doesNotHaveBean(ListenerReevaluacionPrioridad.class);
        });
    }

    @Test
    void conMensajeriaPeroSinIaNoHayReevaluacion() {
        contexto.withPropertyValues("reclamos.mensajeria.habilitada=true").run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(ServicioReevaluacionPrioridad.class)
                    .doesNotHaveBean(ListenerReevaluacionPrioridad.class);
            assertThat(ctx.getBean(SolicitudesReevaluacion.class)).isSameAs(SolicitudesReevaluacion.NINGUNA);
        });
    }

    @Test
    void conMensajeriaEIaLaPrioridadSeReevaluaPorLaCola() {
        contexto.withPropertyValues("reclamos.mensajeria.habilitada=true", "reclamos.prioridad.estrategia=ia",
                "reclamos.prioridad.ia.reevaluacion.espera=15s").run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(ServicioReevaluacionPrioridad.class)
                    .hasSingleBean(ListenerReevaluacionPrioridad.class);
            assertThat(ctx.getBean(PropiedadesReclamos.class).reevaluaPrioridadConIA()).isTrue();
            assertThat(ctx.getBean(SolicitudesReevaluacion.class)).isInstanceOf(OutboxReevaluaciones.class);
            Map<String, Queue> colas = ctx.getBean("topologiaReevaluacion", Declarables.class)
                    .getDeclarablesByType(Queue.class).stream()
                    .collect(java.util.stream.Collectors.toMap(Queue::getName, cola -> cola));
            assertThat(colas).containsOnlyKeys(TopologiaReevaluacion.COLA, TopologiaReevaluacion.COLA_ESPERA,
                    TopologiaReevaluacion.COLA_ERRORES);
            assertThat(colas.get(TopologiaReevaluacion.COLA_ESPERA).getArguments())
                    .containsEntry("x-message-ttl", 15000)
                    .containsEntry("x-dead-letter-routing-key", TopologiaReevaluacion.COLA);
            assertThat(colas.get(TopologiaReevaluacion.COLA).getArguments())
                    .containsEntry("x-dead-letter-routing-key", TopologiaReevaluacion.COLA_ERRORES);
        });
    }

    @Test
    void laReevaluacionSePuedeApagar() {
        contexto.withPropertyValues("reclamos.mensajeria.habilitada=true", "reclamos.prioridad.estrategia=ia",
                "reclamos.prioridad.ia.reevaluacion.habilitada=false").run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(ListenerReevaluacionPrioridad.class);
            assertThat(ctx.getBean(SolicitudesReevaluacion.class)).isSameAs(SolicitudesReevaluacion.NINGUNA);
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
            "reclamos.mensajeria.espera-confirmacion=0s", "reclamos.prioridad.ia.reevaluacion.intentos=0",
            "reclamos.prioridad.ia.reevaluacion.espera=0s"})
    void configuracionInvalidaImpideArrancar(String propiedad) {
        contexto.withPropertyValues(propiedad).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .hasStackTraceContaining(propiedad.substring(0, propiedad.indexOf('=')));
        });
    }
}
