package ar.edu.uade.reclamos.aplicacion.integracion;

import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La comparación con REST 2 ({@code POST /api/duplicados/comparar}). A REST 2 le llegan solo la categoría y la
 * descripción de cada reclamo, con ids "1", "2"… que se eligen acá: ni números de reclamo, ni nombres, ni
 * direcciones, ni fotos. Si REST 2 no responde, no traba el alta: vuelve vacío y quedan los de la misma categoría.
 */
public class ComparadorDuplicadosPorIA implements ComparadorDuplicados {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComparadorDuplicadosPorIA.class);

    private final ClienteServicioPrioridad cliente;

    public ComparadorDuplicadosPorIA(ClienteServicioPrioridad cliente) {
        this.cliente = cliente;
    }

    @Override
    public Optional<Resultado> comparar(String categoria, String descripcion, List<Reclamo> candidatos) {
        if (candidatos.isEmpty() || descripcion == null || descripcion.isBlank()) {
            return Optional.empty();
        }
        List<ClienteServicioPrioridad.TextoCandidato> textos = new ArrayList<>();
        Map<String, Reclamo> porId = new LinkedHashMap<>();
        for (int i = 0; i < candidatos.size(); i++) {
            Reclamo reclamo = candidatos.get(i);
            String id = String.valueOf(i + 1);
            porId.put(id, reclamo);
            textos.add(new ClienteServicioPrioridad.TextoCandidato(id, reclamo.getCategoria().getNombre(),
                    reclamo.getDescripcion()));
        }
        try {
            ComparacionDuplicados comparacion = cliente.compararDuplicados(categoria, descripcion.strip(), textos);
            Reclamo similar = comparacion.similar() == null ? null : porId.get(comparacion.similar());
            return Optional.of(new Resultado(similar, comparacion.motivo()));
        } catch (ServicioExternoNoDisponibleException error) {
            LOGGER.warn("La IA no comparó los posibles duplicados: {}. Quedan los de la misma categoría",
                    error.getMessage());
            return Optional.empty();
        }
    }
}
