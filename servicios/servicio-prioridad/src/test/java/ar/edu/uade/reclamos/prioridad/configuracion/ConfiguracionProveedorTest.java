package ar.edu.uade.reclamos.prioridad.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.prioridad.evaluacion.EvaluadorPrioridad;
import ar.edu.uade.reclamos.prioridad.proveedor.GeminiAdapter;
import ar.edu.uade.reclamos.prioridad.proveedor.OpenAiAdapter;
import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorIA;
import ar.edu.uade.reclamos.prioridad.proveedor.SimuladoAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Cambiar de proveedor es configuración: la Factory entrega el Adapter que corresponde. */
class ConfiguracionProveedorTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PropiedadesIA.class)
    static class ConPropiedades { }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConPropiedades.class, ConfiguracionProveedor.class)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("prioridad.ia.gemini.url=https://gemini.test",
                    "prioridad.ia.gemini.modelo=gemini-3.5-flash-lite", "prioridad.ia.openai.url=https://openai.test",
                    "prioridad.ia.openai.modelo=gpt-5.4-mini");

    @Test
    void porDefectoEsGeminiYArrancaAunqueFalteLaClave() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(EvaluadorPrioridad.class);
            assertThat(ctx.getBean(ProveedorIA.class)).isInstanceOf(GeminiAdapter.class);
            assertThat(ctx.getBean(PropiedadesIA.class).gemini().toString()).contains("sin definir");
        });
    }

    @Test
    void openaiYSimuladoSeEligenPorPropiedad() {
        contexto.withPropertyValues("prioridad.ia.proveedor=openai", "prioridad.ia.openai.clave=sk-secreta")
                .run(ctx -> {
                    assertThat(ctx.getBean(ProveedorIA.class)).isInstanceOf(OpenAiAdapter.class);
                    assertThat(ctx.getBean(PropiedadesIA.class).openai().toString())
                            .doesNotContain("sk-secreta").contains("****");
                });
        contexto.withPropertyValues("prioridad.ia.proveedor=simulado")
                .run(ctx -> assertThat(ctx.getBean(ProveedorIA.class)).isInstanceOf(SimuladoAdapter.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prioridad.ia.proveedor=claude", "prioridad.ia.tiempo-limite=0s",
            "prioridad.ia.gemini.url=", "prioridad.ia.instrucciones=classpath:no-existe.txt"})
    void configuracionInvalidaImpideArrancar(String propiedad) {
        contexto.withPropertyValues(propiedad).run(ctx -> assertThat(ctx).hasFailed());
    }
}
