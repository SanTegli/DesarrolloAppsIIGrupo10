package ar.edu.uade.reclamos.aplicacion.evento;

import ar.edu.uade.reclamos.dominio.evento.EventoDominio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Segundo observador de los eventos de dominio: deja un registro de auditoría en el log.
 * Corre después del commit, de modo que solo audita hechos confirmados; si la transacción se
 * revierte, no registra nada. Es independiente de {@link NotificacionesReclamoListener}:
 * ninguno conoce al otro y el servicio que publica no conoce a ninguno.
 */
@Component
public class AuditoriaListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("auditoria");

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void registrar(EventoDominio evento) {
        LOGGER.info("{} reclamo={} fecha={} detalle={}", evento.nombre(), evento.numeroReclamo(),
                evento.ocurridoEn(), evento);
    }
}
