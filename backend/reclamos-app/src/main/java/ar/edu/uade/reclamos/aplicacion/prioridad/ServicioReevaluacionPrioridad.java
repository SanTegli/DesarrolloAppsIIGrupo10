package ar.edu.uade.reclamos.aplicacion.prioridad;

import ar.edu.uade.reclamos.aplicacion.integracion.ClienteServicioPrioridad;
import ar.edu.uade.reclamos.aplicacion.integracion.SugerenciaPrioridad;
import ar.edu.uade.reclamos.dominio.evento.PrioridadRevisada;
import ar.edu.uade.reclamos.dominio.evento.PublicadorEventos;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import ar.edu.uade.reclamos.dominio.repositorio.ReclamoRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * La segunda oportunidad de la IA. Cuando la IA no respondió durante el alta, el reclamo queda con la prioridad
 * de la categoría y origen {@code PENDIENTE_IA}, y este servicio vuelve a consultarla en segundo plano, con más
 * tiempo, desde el consumidor de la cola.
 *
 * <ul>
 *   <li>Si la IA responde, aplica la misma regla que en el alta (sube, no baja) y publica
 *       {@link PrioridadRevisada}; si la prioridad cambió, los observadores avisan al ciudadano y al área.</li>
 *   <li>Si falla y quedan intentos, pide otro por la cola de espera, en la misma transacción.</li>
 *   <li>Si falla en el último intento, la prioridad de la categoría queda como definitiva ({@code RESPALDO}).</li>
 * </ul>
 *
 * <p>Es idempotente: si el mensaje llega dos veces, o el reclamo ya no espera a la IA, no hace nada.
 */
public class ServicioReevaluacionPrioridad {

    /** Qué pasó con un pedido de reevaluación. */
    public enum Resultado {
        /** La IA respondió y la prioridad quedó revisada. */
        REVISADA,
        /** La IA no respondió; se programó otro intento. */
        REINTENTO_PROGRAMADO,
        /** La IA no respondió en ningún intento; queda la prioridad de la categoría. */
        ABANDONADA,
        /** El reclamo ya no esperaba a la IA (mensaje repetido): no se hizo nada. */
        SIN_PENDIENTE,
        /** No existe un reclamo con ese número. */
        RECLAMO_INEXISTENTE
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicioReevaluacionPrioridad.class);

    private final ReclamoRepository reclamos;
    private final ClienteServicioPrioridad ia;
    private final SolicitudesReevaluacion solicitudes;
    private final PublicadorEventos eventos;
    private final Clock reloj;
    private final int intentos;

    public ServicioReevaluacionPrioridad(ReclamoRepository reclamos, ClienteServicioPrioridad ia,
                                         SolicitudesReevaluacion solicitudes, PublicadorEventos eventos,
                                         Clock reloj, int intentos) {
        if (intentos < 1) {
            throw new IllegalArgumentException("Hace falta al menos un intento");
        }
        this.reclamos = reclamos;
        this.ia = ia;
        this.solicitudes = solicitudes;
        this.eventos = eventos;
        this.reloj = reloj;
        this.intentos = intentos;
    }

    @Transactional
    public Resultado reevaluar(String numero, int intento) {
        Optional<Reclamo> encontrado = reclamos.buscarPorNumero(numero);
        if (encontrado.isEmpty()) {
            LOGGER.warn("Reevaluación de prioridad: no existe el reclamo {}", numero);
            return Resultado.RECLAMO_INEXISTENTE;
        }
        Reclamo reclamo = encontrado.get();
        if (!reclamo.estaPendienteDeIA()) {
            return Resultado.SIN_PENDIENTE;
        }
        SugerenciaPrioridad sugerencia;
        try {
            sugerencia = ia.sugerir(reclamo.getCategoria().getNombre(), reclamo.getDescripcion());
        } catch (ServicioExternoNoDisponibleException error) {
            return sinRespuesta(reclamo, intento, error);
        }
        Prioridad anterior = reclamo.getPrioridad();
        reclamo.completarPrioridadPorIA(sugerencia.prioridad(), sugerencia.motivo());
        Reclamo guardado = reclamos.guardar(reclamo);
        eventos.publicar(new PrioridadRevisada(guardado.getNumero(), anterior, guardado.getPrioridad(),
                guardado.getOrigenPrioridad(), LocalDateTime.now(reloj)));
        LOGGER.info("Prioridad del reclamo {} revisada por la IA en el intento {}: {} -> {}", numero, intento,
                anterior, guardado.getPrioridad());
        return Resultado.REVISADA;
    }

    private Resultado sinRespuesta(Reclamo reclamo, int intento, ServicioExternoNoDisponibleException error) {
        if (intento < intentos) {
            solicitudes.reintentar(reclamo.getNumero(), intento + 1);
            LOGGER.warn("La IA no respondió para el reclamo {} (intento {} de {}): {}. Se vuelve a intentar",
                    reclamo.getNumero(), intento, intentos, error.getMessage());
            return Resultado.REINTENTO_PROGRAMADO;
        }
        reclamo.abandonarRevisionPorIA("La IA no respondió en " + intentos + " intentos; queda la prioridad de "
                + "la categoría.");
        reclamos.guardar(reclamo);
        LOGGER.warn("La IA no respondió para el reclamo {} en {} intentos: queda la prioridad de la categoría",
                reclamo.getNumero(), intentos);
        return Resultado.ABANDONADA;
    }
}
