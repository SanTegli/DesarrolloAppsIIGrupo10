# Capturas del sistema real

Tomadas el 16/11/2026 con `scripts/capturas/capturas-evidencias.mjs` contra `http://localhost:5173`, con la demo levantada con Docker Compose. Los casos están en [evidencias](../README.md).

| Captura | Caso | Qué muestra | Hora |
| --- | --- | --- | --- |
| [01-ingreso](01-ingreso.png) | CP41 | Pantalla de ingreso del sistema real | 11:06:13 a. m. |
| [02-login-fallido](02-login-fallido.png) | CP42 | Login con contraseña incorrecta: el mismo error que con un email inexistente | 11:06:14 a. m. |
| [03-registro](03-registro.png) | CP45 | Registro de un vecino nuevo, antes de enviar | 11:06:14 a. m. |
| [04-verificacion-codigo](04-verificacion-codigo.png) | CP47 | Cuenta nueva: el código de verificación en modo simulado | 11:06:15 a. m. |
| [05-verificacion-confirmada](05-verificacion-confirmada.png) | CP47 | Teléfono confirmado: ya puede crear reclamos | 11:06:16 a. m. |
| [06-alta-sugerencias-georef](06-alta-sugerencias-georef.png) | CP27, CP53 | Alta con las sugerencias reales de Georef y la vista previa de la foto | 11:06:17 a. m. |
| [07-alta-foto-rechazada](07-alta-foto-rechazada.png) | CP54 | Un archivo que no es JPEG ni PNG se rechaza con 400 | 11:06:18 a. m. |
| [08-detalle-ia-maps-foto](08-detalle-ia-maps-foto.png) | CP23, CP28, CP53 | Reclamo creado: prioridad con origen IA, enlace a Google Maps y foto | 11:06:22 a. m. |
| [09-posible-duplicado](09-posible-duplicado.png) | CP58 | Bruno ya reclamó lo mismo: el alta de Ana lo muestra sin datos del autor | 11:06:26 a. m. |
| [10-apoyo-registrado](10-apoyo-registrado.png) | CP60 | Ana apoya el reclamo de Bruno en lugar de crear otro | 11:06:26 a. m. |
| [11-apoyo-contador](11-apoyo-contador.png) | CP60 | Detalle del reclamo de Bruno con el contador de apoyos | 11:06:26 a. m. |
| [12-agente-listado](12-agente-listado.png) | CP12 | Carla ve solo los reclamos de su área | 11:06:27 a. m. |
| [13-agente-en-proceso](13-agente-en-proceso.png) | CP08 | Carla tomó el reclamo y escribe la observación para resolverlo | 11:06:27 a. m. |
| [14-ciudadano-reabre](14-ciudadano-reabre.png) | CP52 | Ana reabre el reclamo resuelto con un motivo | 11:06:28 a. m. |
| [15-ciudadano-cerrado](15-ciudadano-cerrado.png) | CP08, CP52 | Ana confirma la segunda solución: historial completo y la reapertura ya no se ofrece | 11:06:29 a. m. |
| [16-avisos](16-avisos.png) | CP14 | Avisos que recibió Ana | 11:06:29 a. m. |
| [17-admin-sin-asignar](17-admin-sin-asignar.png) | CP06, CP18 | Residuos en Ezpeleta: ningún área lo cubre y queda para la administración | 11:06:32 a. m. |
| [18-admin-asignacion-manual](18-admin-asignacion-manual.png) | CP06 | Elena asigna el área a mano | 11:06:32 a. m. |
| [19-admin-cuenta-bloqueada](19-admin-cuenta-bloqueada.png) | CP51 | Elena bloquea la cuenta de Bruno | 11:06:33 a. m. |
| [20-ciudadano-bloqueado](20-ciudadano-bloqueado.png) | CP51 | Bruno, bloqueado, no puede crear reclamos | 11:06:33 a. m. |
| [21-tablero-publico](21-tablero-publico.png) | CP63 | Tablero público, sin iniciar sesión | 11:06:33 a. m. |
| [22-tablero-gestion](22-tablero-gestion.png) | CP65 | Tablero de gestión de la administradora | 11:06:34 a. m. |
| [23-swagger-rest2-gemini](23-swagger-rest2-gemini.png) | CP22 | Swagger de REST 2: una evaluación real con Gemini | 11:06:38 a. m. |
| [24-wsdl](24-wsdl.png) | CP16 | WSDL del servicio de cobertura | 11:06:38 a. m. |
| [25-mailpit](25-mailpit.png) | CP33 | Correos de los avisos en Mailpit | 11:06:38 a. m. |
| [26-rabbitmq-exchanges](26-rabbitmq-exchanges.png) | CP33 | Exchanges en la consola de RabbitMQ | 11:06:39 a. m. |
| [27-rabbitmq-colas](27-rabbitmq-colas.png) | CP33, CP36 | Colas de RabbitMQ, con la cola de errores | 11:06:39 a. m. |
