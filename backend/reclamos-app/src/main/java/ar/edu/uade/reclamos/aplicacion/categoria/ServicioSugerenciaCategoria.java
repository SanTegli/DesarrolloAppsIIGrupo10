package ar.edu.uade.reclamos.aplicacion.categoria;

import ar.edu.uade.reclamos.aplicacion.integracion.ClienteServicioPrioridad;
import ar.edu.uade.reclamos.aplicacion.integracion.SugerenciaCategoria;
import ar.edu.uade.reclamos.dominio.evento.CategoriaSugerida;
import ar.edu.uade.reclamos.dominio.evento.PublicadorEventos;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.Categoria;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.repositorio.CategoriaRepository;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * La IA sugiere la categoría de un reclamo de "Otro problema" (PR 26), desde el consumidor de la cola.
 *
 * <ul>
 *   <li>Le manda a REST 2 lo que escribió el vecino, la descripción y los nombres de las categorías concretas
 *       (todas menos "otra"). Nada que identifique al vecino.</li>
 *   <li>Si responde, guarda la sugerencia en el reclamo. La categoría no cambia: la cambia un administrador.</li>
 *   <li>Si falla y quedan intentos, pide otro por la cola de espera. En el último, guarda que la IA no respondió,
 *       para que el administrador no espere una sugerencia que no va a llegar.</li>
 * </ul>
 *
 * <p>Es idempotente: si el mensaje llega dos veces, o el reclamo ya se recategorizó, no hace nada.
 */
public class ServicioSugerenciaCategoria {

    /** Qué pasó con un pedido de sugerencia. */
    public enum Resultado {
        /** La IA respondió, con una categoría o sin ninguna. */
        SUGERIDA,
        /** La IA no respondió; se programó otro intento. */
        REINTENTO_PROGRAMADO,
        /** La IA no respondió en ningún intento. */
        ABANDONADA,
        /** El reclamo ya tenía sugerencia o ya no es de "otra": no se hizo nada. */
        SIN_PENDIENTE,
        /** No existe un reclamo con ese número. */
        RECLAMO_INEXISTENTE
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioSugerenciaCategoria.class);

    private final ReclamoRepository reclamos;
    private final CategoriaRepository categorias;
    private final ClienteServicioPrioridad ia;
    private final SolicitudesSugerenciaCategoria solicitudes;
    private final PublicadorEventos eventos;
    private final Clock reloj;
    private final int intentos;

    public ServicioSugerenciaCategoria(ReclamoRepository reclamos, CategoriaRepository categorias,
                                       ClienteServicioPrioridad ia, SolicitudesSugerenciaCategoria solicitudes,
                                       PublicadorEventos eventos, Clock reloj, int intentos) {
        if (intentos < 1) {
            throw new IllegalArgumentException("Hace falta al menos un intento");
        }
        this.reclamos = reclamos;
        this.categorias = categorias;
        this.ia = ia;
        this.solicitudes = solicitudes;
        this.eventos = eventos;
        this.reloj = reloj;
        this.intentos = intentos;
    }

    @Transactional
    public Resultado sugerir(String numero, int intento) {
        Optional<Reclamo> encontrado = reclamos.buscarPorNumero(numero);
        if (encontrado.isEmpty()) {
            LOGGER.warn("Sugerencia de categoría: no existe el reclamo {}", numero);
            return Resultado.RECLAMO_INEXISTENTE;
        }
        Reclamo reclamo = encontrado.get();
        if (!reclamo.getCategoria().esOtra() || reclamo.tieneSugerenciaDeCategoria()) {
            return Resultado.SIN_PENDIENTE;
        }
        List<Categoria> concretas = categorias.buscarTodas().stream().filter(categoria -> !categoria.esOtra())
                .toList();
        SugerenciaCategoria sugerencia;
        try {
            sugerencia = ia.sugerirCategoria(reclamo.getCategoriaPropuesta(), reclamo.getDescripcion(),
                    concretas.stream().map(Categoria::getNombre).toList());
        } catch (ServicioExternoNoDisponibleException error) {
            return sinRespuesta(reclamo, intento, error);
        }
        Categoria sugerida = sugerencia.categoria() == null ? null : concretas.stream()
                .filter(categoria -> categoria.getNombre().equals(sugerencia.categoria()))
                .findFirst()
                .orElse(null);
        registrar(reclamo, sugerida, sugerencia.motivo());
        LOGGER.info("Categoría sugerida por la IA para el reclamo {} en el intento {}: {}", numero, intento,
                sugerida == null ? "ninguna" : sugerida.getNombre());
        return Resultado.SUGERIDA;
    }

    private Resultado sinRespuesta(Reclamo reclamo, int intento, ServicioExternoNoDisponibleException error) {
        if (intento < intentos) {
            solicitudes.reintentar(reclamo.getNumero(), intento + 1);
            LOGGER.warn("La IA no sugirió categoría para el reclamo {} (intento {} de {}): {}. Se vuelve a intentar",
                    reclamo.getNumero(), intento, intentos, error.getMessage());
            return Resultado.REINTENTO_PROGRAMADO;
        }
        registrar(reclamo, null, "La IA no respondió en " + intentos + " intentos: elegí la categoría a mano.");
        LOGGER.warn("La IA no sugirió categoría para el reclamo {} en {} intentos", reclamo.getNumero(), intentos);
        return Resultado.ABANDONADA;
    }

    private void registrar(Reclamo reclamo, Categoria sugerida, String motivo) {
        if (reclamo.registrarSugerenciaCategoria(sugerida, motivo)) {
            Reclamo guardado = reclamos.guardar(reclamo);
            eventos.publicar(new CategoriaSugerida(guardado.getNumero(), sugerida == null ? null : sugerida.getId(),
                    sugerida == null ? null : sugerida.getNombre(), LocalDateTime.now(reloj)));
        }
    }
}
