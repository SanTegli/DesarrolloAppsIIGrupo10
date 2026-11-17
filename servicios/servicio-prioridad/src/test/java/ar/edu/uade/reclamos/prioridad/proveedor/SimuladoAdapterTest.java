package ar.edu.uade.reclamos.prioridad.proveedor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void paraLaCategoriaBuscaLasPalabrasDeCadaUnaSinTildesYSiNoHayDiceNinguna() {
        FormatoRespuesta formato = FormatoRespuesta.categoria(List.of("Bache", "Árbol caído o con riesgo"));

        RespuestaIA arbol = simulado.sugerir(new ConsultaIA("", "<propuesta>Rama</propuesta>\n<descripcion>Un arbol "
                + "se esta por caer</descripcion>", formato));
        RespuestaIA nada = simulado.sugerir(new ConsultaIA("", "<descripcion>Ruido de una fiesta</descripcion>",
                formato));

        assertThat(arbol.valor()).isEqualTo("Árbol caído o con riesgo");
        assertThat(arbol.motivo()).startsWith("Simulado:");
        assertThat(nada.valor()).isEqualTo(FormatoRespuesta.NINGUNA);
    }
}
