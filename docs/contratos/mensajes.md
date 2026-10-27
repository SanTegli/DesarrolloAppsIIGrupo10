# Contrato de mensajes: RabbitMQ

Borrador del PR 8; lo implementa el PR 12. El backend publica un mensaje por cada aviso que genera, y
`servicio-notificaciones` lo consume y envía el correo a Mailpit.

## Topología

| Elemento | Nombre | Tipo |
| --- | --- | --- |
| Exchange | `reclamos.eventos` | `topic`, durable |
| Cola | `notificaciones.email` | durable, ligada con `aviso.#` |
| Exchange de errores | `reclamos.eventos.dlx` | `fanout`, durable |
| Cola de errores | `notificaciones.email.dlq` | durable, ligada a `reclamos.eventos.dlx` |

## Claves de ruteo

Una por evento de dominio que genera avisos:

| Evento de dominio | Clave de ruteo |
| --- | --- |
| `ReclamoCreado` | `aviso.reclamo-creado` |
| `ReclamoAsignado` | `aviso.reclamo-asignado` |
| `EstadoReclamoCambiado` | `aviso.estado-cambiado` |
| `ReclamoResuelto` | `aviso.reclamo-resuelto` |
| `ReclamoVencido` | `aviso.reclamo-vencido` |

## Mensaje

JSON en UTF-8, con `content-type: application/json` y entrega persistente.

```json
{
  "eventId": "6f1c2a9e-4b7d-4e1f-9a52-3c8d0e7b1a44",
  "version": 1,
  "tipoEvento": "ReclamoAsignado",
  "reclamoNumero": "REC-4F2A91BC",
  "destinatario": { "id": 1, "nombre": "Ana Pérez", "email": "ana@example.org" },
  "mensaje": "Tu reclamo REC-4F2A91BC fue asignado al area Alumbrado.",
  "fechaEvento": "2026-10-26T15:00:00-03:00"
}
```

| Campo | Regla |
| --- | --- |
| `eventId` | UUID de la fila de outbox: uno por aviso y destinatario. Lo usa el consumidor para no procesar dos veces |
| `version` | Versión del formato; empieza en 1. Un consumidor rechaza versiones que no conoce |
| `tipoEvento` | Nombre del evento de dominio que generó el aviso |
| `destinatario` | Quién recibe el correo. Nunca DNI, teléfono ni descripción del reclamo |
| `fechaEvento` | ISO-8601 **con** zona horaria, a diferencia de la API REST |

## Garantías y fallas

- **Productor:** el aviso y la fila de outbox se guardan en la misma transacción. Una tarea publica las
  filas pendientes con confirmación del broker y recién entonces las marca publicadas. Si RabbitMQ está
  caído, las filas esperan.
- **Consumidor:** registra cada `eventId` con restricción única antes de confirmar el mensaje; un repetido
  se confirma sin reenviar el correo. Tres intentos con espera creciente (1, 2 y 4 segundos); después, a la
  cola de errores. Un mensaje inválido va directo a la cola de errores, sin reintentos.
- **No hay entrega exactamente una vez:** si el consumidor cae después de enviar el correo y antes de
  registrar el `eventId`, el correo se repite al reintentar.
