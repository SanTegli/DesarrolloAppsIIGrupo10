package ar.edu.uade.reclamos.notificaciones.aviso;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Qué mensajes ya se procesaron, por {@code eventId}, en la base propia del servicio (tabla
 * {@code mensajes_procesados}, con el {@code eventId} como clave primaria). Es lo que hace idempotente al
 * consumidor: un mensaje repetido se reconoce y no se manda dos veces.
 */
public class RegistroProcesados {

    private final JdbcTemplate jdbc;

    public RegistroProcesados(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean yaProcesado(String eventId) {
        Integer cantidad = jdbc.queryForObject("select count(*) from mensajes_procesados where event_id = ?",
                Integer.class, eventId);
        return cantidad != null && cantidad > 0;
    }

    /** @return {@code false} si otro hilo lo registró antes (la clave primaria lo impide) */
    public boolean registrar(String eventId, String tipoEvento, Instant procesadoEn) {
        try {
            jdbc.update("insert into mensajes_procesados (event_id, tipo_evento, procesado_en) values (?, ?, ?)",
                    eventId, tipoEvento, Timestamp.from(procesadoEn));
            return true;
        } catch (DuplicateKeyException repetido) {
            return false;
        }
    }
}
