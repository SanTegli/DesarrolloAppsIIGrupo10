package ar.edu.uade.reclamos.prioridad.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.prioridad.proveedor.ConsultaIA;
import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaIA;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaInvalidaException;
import org.junit.jupiter.api.Test;

/** Lo que el servicio controla de la respuesta del modelo, sin importar el proveedor. */
class EvaluadorPrioridadTest {

    /** Proveedor que contesta siempre lo mismo y guarda la última consulta. */
    private static final class ProveedorFijo implements ProveedorIA {
        private final RespuestaIA respuesta;
        private ConsultaIA recibida;

        ProveedorFijo(String prioridad, String motivo) {
            this.respuesta = new RespuestaIA(prioridad, motivo);
        }

        @Override
        public String nombre() {
            return "fijo";
        }

        @Override
        public String modelo() {
            return "modelo-de-prueba";
        }

        @Override
        public RespuestaIA sugerir(ConsultaIA consulta) {
            recibida = consulta;
            return respuesta;
        }
    }

    private final InstruccionesPrioridad instrucciones = new InstruccionesPrioridad("Criterios.");

    @Test
    void devuelveLaPrioridadValidadaYQuienLaSugirio() {
        ProveedorFijo proveedor = new ProveedorFijo(" critica ", "  Cables   expuestos. ");

        Evaluacion evaluacion = new EvaluadorPrioridad(instrucciones, proveedor).evaluar("Luminaria rota", "Cables");

        assertThat(evaluacion).isEqualTo(
                new Evaluacion(Prioridad.CRITICA, "Cables expuestos.", "fijo", "modelo-de-prueba"));
        assertThat(proveedor.recibida.instrucciones()).isEqualTo("Criterios.");
        assertThat(proveedor.recibida.reclamo()).contains("<categoria>Luminaria rota</categoria>")
                .contains("<descripcion>\nCables\n</descripcion>");
    }

    @Test
    void prioridadQueNoExisteEsRespuestaInvalida() {
        EvaluadorPrioridad evaluador = new EvaluadorPrioridad(instrucciones, new ProveedorFijo("URGENTISIMA", "x"));

        assertThatThrownBy(() -> evaluador.evaluar("Bache", "Pozo"))
                .isInstanceOf(RespuestaInvalidaException.class).hasMessageContaining("no existe");
    }

    @Test
    void sinPrioridadOSinMotivoEsRespuestaInvalida() {
        assertThatThrownBy(() -> new EvaluadorPrioridad(instrucciones, new ProveedorFijo(null, "x"))
                .evaluar("Bache", "Pozo")).isInstanceOf(RespuestaInvalidaException.class);
        assertThatThrownBy(() -> new EvaluadorPrioridad(instrucciones, new ProveedorFijo("ALTA", " "))
                .evaluar("Bache", "Pozo")).isInstanceOf(RespuestaInvalidaException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void motivoLargoSeRecortaA300Caracteres() {
        Evaluacion evaluacion = new EvaluadorPrioridad(instrucciones, new ProveedorFijo("ALTA", "a".repeat(500)))
                .evaluar("Bache", "Pozo");

        assertThat(evaluacion.motivo()).hasSize(EvaluadorPrioridad.LONGITUD_MAXIMA_MOTIVO).endsWith("…");
    }
}
