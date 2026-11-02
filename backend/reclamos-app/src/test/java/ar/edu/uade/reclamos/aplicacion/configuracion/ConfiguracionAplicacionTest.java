package ar.edu.uade.reclamos.aplicacion.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ar.edu.uade.reclamos.aplicacion.estrategia.*;
import ar.edu.uade.reclamos.dominio.estrategia.*;
import ar.edu.uade.reclamos.dominio.fabrica.ReclamoFactory;
import ar.edu.uade.reclamos.dominio.modelo.*;
import ar.edu.uade.reclamos.aplicacion.integracion.CoberturaLocal;
import ar.edu.uade.reclamos.aplicacion.integracion.CoberturaSoap;
import ar.edu.uade.reclamos.dominio.integracion.CatalogoCobertura;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfiguracionAplicacionTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionAplicacion.class)
            .withBean(ReclamoRepository.class, () -> mock(ReclamoRepository.class))
            .withBean(AreaMunicipalRepository.class, () -> mock(AreaMunicipalRepository.class));

    @Test
    void valoresPredeterminadosRegistranUnaEstrategiaPorContratoFactoryYReloj() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(EstrategiaPrioridad.class)
                    .hasSingleBean(EstrategiaAsignacion.class).hasSingleBean(ReclamoFactory.class)
                    .hasSingleBean(Clock.class);
            assertThat(ctx.getBean(EstrategiaPrioridad.class)).isInstanceOf(PrioridadPorCategoria.class);
            assertThat(ctx.getBean(EstrategiaAsignacion.class)).isInstanceOf(AsignacionPorJurisdiccion.class);
            assertThat(ctx.getBean(CatalogoCobertura.class)).isInstanceOf(CoberturaLocal.class);
            assertThat(ctx.getBean(Clock.class).getZone()).isEqualTo(ZoneId.systemDefault());
        });
    }

    @Test
    void seleccionaLasEstrategiasAlternativasPorPropiedades() {
        contexto.withPropertyValues("reclamos.prioridad.estrategia=palabras-clave",
                "reclamos.asignacion.estrategia=carga-trabajo").run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(EstrategiaPrioridad.class)
                    .hasSingleBean(EstrategiaAsignacion.class);
            assertThat(ctx.getBean(EstrategiaPrioridad.class)).isInstanceOf(PrioridadPorPalabrasClave.class);
            assertThat(ctx.getBean(EstrategiaAsignacion.class)).isInstanceOf(AsignacionPorCargaDeTrabajo.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"reclamos.prioridad.estrategia=desconocida", "reclamos.asignacion.estrategia=desconocida",
            "reclamos.prioridad.estrategia=", "reclamos.asignacion.estrategia=",
            "reclamos.cobertura.origen=desconocida", "reclamos.cobertura.origen=",
            "reclamos.cobertura.url=", "reclamos.cobertura.tiempo-limite=0s",
            "reclamos.prioridad.ia.url=", "reclamos.prioridad.ia.tiempo-respuesta=0s",
            "reclamos.prioridad.ia.circuito.llamadas=0"})
    void configuracionInvalidaImpideArrancarConMensajeClaro(String propiedad) {
        contexto.withPropertyValues(propiedad).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining(propiedad.substring(0, propiedad.indexOf('=')));
        });
    }

    @Test
    void prioridadPorIaQuedaEnvueltaEnElRespaldoConLosTiemposConfigurados() {
        contexto.withPropertyValues("reclamos.prioridad.estrategia=ia",
                "reclamos.prioridad.ia.url=http://servicio-prioridad:8082").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EstrategiaPrioridad.class)).isInstanceOf(PrioridadConRespaldo.class);
            PropiedadesReclamos.Ia ia = ctx.getBean(PropiedadesReclamos.class).prioridad().ia();
            assertThat(ia.url()).isEqualTo("http://servicio-prioridad:8082");
            assertThat(ia.tiempoConexion()).isEqualTo(Duration.ofSeconds(1));
            assertThat(ia.tiempoRespuesta()).isEqualTo(Duration.ofSeconds(6));
            assertThat(ia.circuito().llamadas()).isEqualTo(5);
            assertThat(ia.circuito().espera()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void coberturaPorDefectoEsLocalYApuntaAlServicioSoapDeDesarrollo() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            PropiedadesReclamos.Cobertura cobertura = ctx.getBean(PropiedadesReclamos.class).cobertura();
            assertThat(cobertura.origen()).isEqualTo("local");
            assertThat(cobertura.url()).isEqualTo("http://localhost:8081/ws");
            assertThat(cobertura.tiempoLimite()).isEqualTo(Duration.ofSeconds(2));
        });
    }

    @Test
    void coberturaSoapUsaElAdapterConLaUrlYElTiempoLimiteConfigurados() {
        contexto.withPropertyValues("reclamos.cobertura.origen=soap",
                "reclamos.cobertura.url=http://servicio-cobertura:8081/ws",
                "reclamos.cobertura.tiempo-limite=3s").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(CatalogoCobertura.class)).isInstanceOf(CoberturaSoap.class);
            PropiedadesReclamos.Cobertura cobertura = ctx.getBean(PropiedadesReclamos.class).cobertura();
            assertThat(cobertura.url()).isEqualTo("http://servicio-cobertura:8081/ws");
            assertThat(cobertura.tiempoLimite()).isEqualTo(Duration.ofSeconds(3));
        });
    }

    @Test
    void vencimientosTienenValoresPredeterminadosYAceptanOtros() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            PropiedadesReclamos.Vencimientos vencimientos = ctx.getBean(PropiedadesReclamos.class).vencimientos();
            assertThat(vencimientos.habilitado()).isTrue();
            assertThat(vencimientos.intervaloMs()).isEqualTo(60000);
        });
        contexto.withPropertyValues("reclamos.vencimientos.habilitado=false",
                "reclamos.vencimientos.intervalo-ms=5000").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            PropiedadesReclamos.Vencimientos vencimientos = ctx.getBean(PropiedadesReclamos.class).vencimientos();
            assertThat(vencimientos.habilitado()).isFalse();
            assertThat(vencimientos.intervaloMs()).isEqualTo(5000);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"reclamos.vencimientos.intervalo-ms=0", "reclamos.vencimientos.intervalo-ms=-1",
            "reclamos.vencimiento.habilitado=false", "reclamos.vencimientos.intervalo=60000"})
    void vencimientosMalConfiguradosImpidenArrancar(String propiedad) {
        contexto.withPropertyValues(propiedad).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void factoryUsaElRelojInyectadoParaCreacionYSla() {
        Clock fijo = Clock.fixed(Instant.parse("2026-10-05T13:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        contexto.withBean(Clock.class, () -> fijo).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(Clock.class);
            assertThat(ctx.getBean(Clock.class)).isSameAs(fijo);
            Municipio municipio = new Municipio("Quilmes", "Buenos Aires");
            Reclamo reclamo = ctx.getBean(ReclamoFactory.class).crear(
                    new Ciudadano("30111222", "Ana", "Pérez", "ana@example.org", null),
                    new Categoria("Bache", "Pozo", 48, Prioridad.BAJA), new Barrio("Bernal", municipio),
                    "Pozo en la calle", "Belgrano 450");
            assertThat(reclamo.getFechaCreacion()).isEqualTo(LocalDateTime.of(2026, 10, 5, 10, 0));
            assertThat(reclamo.getFechaLimite()).isEqualTo(reclamo.getFechaCreacion().plusHours(48));
        });
    }
}
