# Capturas del sistema real

Tomadas el 17/11/2026 con `scripts/capturas/capturas-evidencias.mjs` contra `http://localhost:5173`, con la demo levantada con Docker Compose. Los casos están en [evidencias](../README.md).

| Captura | Caso | Qué muestra | Hora |
| --- | --- | --- | --- |
| [01-ingreso](01-ingreso.png) | CP41 | Pantalla de ingreso del sistema real | 12:45:32 a. m. |
| [02-login-fallido](02-login-fallido.png) | CP42 | Login con contraseña incorrecta: el mismo error que con un email inexistente | 12:45:33 a. m. |
| [03-registro](03-registro.png) | CP45 | Registro de un vecino nuevo, antes de enviar | 12:45:34 a. m. |
| [04-verificacion-codigo](04-verificacion-codigo.png) | CP47 | Cuenta nueva: el código de verificación en modo simulado | 12:45:35 a. m. |
| [05-verificacion-confirmada](05-verificacion-confirmada.png) | CP47 | Teléfono confirmado: ya puede crear reclamos | 12:45:35 a. m. |
| [06-alta-sugerencias-georef](06-alta-sugerencias-georef.png) | CP27, CP53 | Alta con las sugerencias reales de Georef y la vista previa de la foto | 12:45:36 a. m. |
| [07-alta-foto-rechazada](07-alta-foto-rechazada.png) | CP54 | Un archivo que no es JPEG ni PNG se rechaza con 400 | 12:45:37 a. m. |
| [08-detalle-ia-maps-foto](08-detalle-ia-maps-foto.png) | CP23, CP28, CP53 | Reclamo creado: prioridad con origen IA, enlace a Google Maps y foto | 12:45:48 a. m. |
| [09-posible-duplicado](09-posible-duplicado.png) | CP58, CP75 | Ana carga "Otro problema" y la IA encuentra el bache de Bruno, sin datos del autor | 12:45:56 a. m. |
| [10-apoyo-registrado](10-apoyo-registrado.png) | CP60, CP76 | Ana se asocia al reclamo de Bruno en lugar de crear otro | 12:45:56 a. m. |
| [11-apoyo-contador](11-apoyo-contador.png) | CP60 | Detalle del reclamo de Bruno con el contador de apoyos | 12:45:56 a. m. |
| [12-agente-listado](12-agente-listado.png) | CP12 | Carla ve solo los reclamos de su área | 12:45:57 a. m. |
| [13-agente-en-proceso](13-agente-en-proceso.png) | CP08 | Carla tomó el reclamo y escribe la observación para resolverlo | 12:45:58 a. m. |
| [14-ciudadano-reabre](14-ciudadano-reabre.png) | CP52 | Ana reabre el reclamo resuelto con un motivo | 12:45:58 a. m. |
| [15-ciudadano-cerrado](15-ciudadano-cerrado.png) | CP08, CP52 | Ana confirma la segunda solución: historial completo y la reapertura ya no se ofrece | 12:45:59 a. m. |
| [16-avisos](16-avisos.png) | CP14 | Avisos que recibió Ana | 12:46:00 a. m. |
| [17-admin-sin-asignar](17-admin-sin-asignar.png) | CP06, CP18 | Luminaria en Avellaneda: ningún área de ese municipio la cubre y queda para la administración | 12:46:02 a. m. |
| [18-admin-asignacion-manual](18-admin-asignacion-manual.png) | CP06 | Elena asigna el área a mano | 12:46:02 a. m. |
| [19-admin-cuenta-bloqueada](19-admin-cuenta-bloqueada.png) | CP51 | Elena bloquea la cuenta de Bruno | 12:46:02 a. m. |
| [20-ciudadano-bloqueado](20-ciudadano-bloqueado.png) | CP51 | Bruno, bloqueado, no puede crear reclamos | 12:46:03 a. m. |
| [28-otro-problema-alta](28-otro-problema-alta.png) | CP73 | Ana elige "Otro problema" y escribe qué tipo de problema es | 12:46:03 a. m. |
| [29-otro-problema-sugerencia-ia](29-otro-problema-sugerencia-ia.png) | CP73, CP74 | Elena ve la categoría que sugirió la IA, ya elegida en la lista | 12:46:11 a. m. |
| [30-otro-problema-recategorizado](30-otro-problema-recategorizado.png) | CP74 | Recategorizado: el reclamo se asignó solo al área de la categoría nueva | 12:46:11 a. m. |
| [21-tablero-publico](21-tablero-publico.png) | CP63 | Tablero público, sin iniciar sesión | 12:46:12 a. m. |
| [22-tablero-gestion](22-tablero-gestion.png) | CP65 | Tablero de gestión de la administradora | 12:46:12 a. m. |
| [23-swagger-rest2-gemini](23-swagger-rest2-gemini.png) | CP22 | Swagger de REST 2: una evaluación real con Gemini | 12:46:16 a. m. |
| [24-wsdl](24-wsdl.png) | CP16 | WSDL del servicio de cobertura | 12:46:17 a. m. |
| [25-mailpit](25-mailpit.png) | CP33 | Correos de los avisos en Mailpit | 12:46:17 a. m. |
| [26-rabbitmq-exchanges](26-rabbitmq-exchanges.png) | CP33 | Exchanges en la consola de RabbitMQ | 12:46:18 a. m. |
| [27-rabbitmq-colas](27-rabbitmq-colas.png) | CP33, CP36 | Colas de RabbitMQ, con la cola de errores | 12:46:18 a. m. |
