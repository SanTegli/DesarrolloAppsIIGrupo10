-- Mensajes ya procesados por el consumidor. El eventId como clave primaria hace imposible registrarlo dos veces.
CREATE TABLE IF NOT EXISTS mensajes_procesados (
    event_id     VARCHAR(36) NOT NULL PRIMARY KEY,
    tipo_evento  VARCHAR(40) NOT NULL,
    procesado_en TIMESTAMP   NOT NULL
);
