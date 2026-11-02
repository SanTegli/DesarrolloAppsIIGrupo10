package ar.edu.uade.reclamos.prioridad.proveedor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimuladoAdapterTest {

    private final SimuladoAdapter simulado = new SimuladoAdapter();

    @Test
    void buscaPalabrasYLoDiceEnElMotivo() {
        RespuestaIA cables = simulado.sugerir(new ConsultaIA("", "<descripcion>Hay CABLES colgando</descripcion>"));
        RespuestaIA nada = simulado.sugerir(new ConsultaIA("", "<descripcion>Cartel despintado</descripcion>"));

        assertThat(cables.prioridad()).isEqualTo("CRITICA");
        assertThat(cables.motivo()).startsWith("Simulado:");
        assertThat(nada.prioridad()).isEqualTo("BAJA");
        assertThat(simulado.nombre()).isEqualTo("simulado");
    }
}
