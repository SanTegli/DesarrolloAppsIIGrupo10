# Guion de la demo

La demo recorre **un solo reclamo** a través de las cinco integraciones de la Segunda Parte. Así se ve el
flujo completo y no una captura suelta de cada pieza. Después se apagan servicios a propósito, para
mostrar que el sistema sigue andando.

Duración: unos 12 minutos para el recorrido y 5 para las fallas.

## Antes de la demo

**El día anterior**, en la computadora de la demo:

1. Completar `.env` desde `.env.example`, con `GEMINI_API_KEY` (ver
   [README_INSTRUCTIVO.md](../README_INSTRUCTIVO.md), «Probar el servicio de prioridad con IA»).
2. Ejecutar `docker compose up --build` una vez. La primera construcción baja las imágenes y las dependencias de
   Maven y tarda varios minutos. Las siguientes salen de la caché.
3. Con todo levantado, en otra terminal: `docker compose run --rm verificar-demo`. Tiene que terminar con
   `0 fallas` (ver [Verificación automática](#verificación-automática)).

**Media hora antes:**

1. `docker compose up` (sin `--build` si no cambió el código). Esperar a que `docker compose ps` muestre
   `healthy` en el backend y el frontend.
2. Correr otra vez `docker compose run --rm verificar-demo`.
3. Abrir estas pestañas en el navegador:

| Pestaña | Dirección | Para qué |
| --- | --- | --- |
| Interfaz | `http://localhost:5173` | El recorrido del ciudadano, la agente y la administradora |
| Mailpit | `http://localhost:8025` | Los correos que manda el consumidor |
| RabbitMQ | `http://localhost:15672` | El exchange, las colas y la cola de errores. Usuario y contraseña de `.env` |
| WSDL | `http://localhost:8081/ws/cobertura.wsdl` | El contrato SOAP |
| Swagger de REST 2 | `http://localhost:8082/swagger-ui.html` | El servicio de prioridad con IA |
| Swagger de REST 1 | `http://localhost:8080/swagger-ui.html` | La API de reclamos |

La verificación deja reclamos de prueba de Ana Pérez. Si se prefiere arrancar con la base limpia, ver
«Cómo apagar todo» en el instructivo: `docker compose down -v` borra los datos y requiere decidirlo.

**Memoria.** Cada servicio Java tiene un tope de memoria (`JAVA_TOOL_OPTIONS` en `docker-compose.yml`): 512 MB
de heap el backend y 256 MB cada servicio. Sumando MySQL y RabbitMQ, el total estimado ronda los 2,5 GB: Docker
Desktop con 4 GB asignados alcanza. El trabajo «Demo completa» del CI muestra el consumo real de cada
contenedor en el paso «Memoria de cada contenedor».

## Recorrido

| Paso | Qué se hace | Qué se muestra | Qué integración o patrón |
| --- | --- | --- | --- |
| 1 | Abrir el diagrama de componentes de [arquitectura.md](arquitectura.md#componentes-e-interfaces) | Cuatro aplicaciones propias y sus contratos | CBD: interfaces provistas y requeridas |
| 2 | En la interfaz, entrar como **Ana Pérez** (`ana@example.org`, contraseña `demo1234`). **Crear reclamo**: Luminaria rota, barrio Bernal, dirección "Belgrano 450" | La pantalla de ingreso; después, Ana arriba a la derecha con **Salir** | Login con sesión del servidor (Spring Security) |
| 3 | Tocar **Buscar en el mapa** | Varias sugerencias, porque Belgrano 450 existe en más de una localidad; cada una con "Ver en el mapa" | **API externa** (Georef), Adapter y proxy de caché |
| 4 | Elegir la sugerencia de Bernal. Descripción: "Hay un cable colgando del poste de luz y chispea cuando llueve, justo frente a la escuela" | — | — |
| 5 | **Crear reclamo** | Detalle: prioridad alta o crítica "sugerida por IA" con su motivo, área Alumbrado, dirección normalizada y **Ver en Google Maps** | **IA** (REST 2 con Gemini) y **SOAP** (cobertura) |
| 6 | Pestaña Mailpit | Los correos "lo recibimos" y "ya tiene un área a cargo" para Ana, y el aviso para Carla Gómez | **Mensajería**: outbox, RabbitMQ y consumidor |
| 7 | Pestaña RabbitMQ: **Exchanges** → `reclamos.eventos`, **Queues** → `notificaciones.email` | El gráfico de mensajes publicados y entregados; `notificaciones.email.dlq` vacía | Publicar y suscribir, cola de errores |
| 8 | Pestañas del WSDL y de los dos Swagger | `ConsultarCobertura`, `POST /api/prioridades/evaluar` y los endpoints de REST 1 | **Contratos**: WSDL y OpenAPI |
| 9 | **Salir** y entrar como **Carla Gómez** (`carla@example.org`, agente de Alumbrado): tomar el reclamo y resolverlo | El historial suma dos pasos | Cada rol ve y hace lo suyo: lo decide el dominio con el usuario de la sesión |
| 10 | Volver a Mailpit | "cambió de estado" y "está resuelto" para Ana | El mismo flujo de mensajería, otro evento |
| 11 | Entrar como Ana, abrir el reclamo resuelto y **Reabrir** con el motivo "Sigue apagado". Que Carla lo resuelva otra vez | La segunda vez, el botón **Reabrir** ya no aparece: solo **Confirmar solución** | Reaperturas con límite (dominio) |
| 12 | **Crear una cuenta** nueva e ir a **Crear reclamo** | Pide verificar el teléfono. Con el bot: código, enlace a Telegram y "Compartir mi teléfono". Sin bot: el número en un formulario. Repetir con el número de otra cuenta: "Ese número ya está verificado en otra cuenta" | **Segunda API externa** (Telegram), Adapter |
| 13 | Entrar como **Elena Ruiz** → **Administración** → **Cuentas de ciudadanos** → **Bloquear** la cuenta nueva | Esa cuenta entra y ve sus reclamos, pero **Crear reclamo** muestra el aviso de cuenta bloqueada | Chain of Responsibility de los controles de uso |

Para el paso 5: la IA puede subir la prioridad de la categoría pero nunca bajarla. Si sugiere una menor, el
detalle dice "por categoría" y el motivo lo explica. Si dice "por categoría (la IA no respondió)", el servicio
de IA no contestó: revisar la clave antes de seguir (ver [Si algo sale mal](#si-algo-sale-mal)).

## Fallas a propósito

Elegir dos o tres. Cada una se hace en una terminal desde la raíz del repositorio y se deshace con `start`.

| Falla | Cómo provocarla | Qué se ve | Cómo se vuelve |
| --- | --- | --- | --- |
| IA caída | `docker compose stop servicio-prioridad` y crear un reclamo | Responde igual; prioridad "por categoría (la IA no respondió)" | `docker compose start servicio-prioridad` |
| SOAP caído | `docker compose stop servicio-cobertura` y crear un reclamo | Queda `INGRESADO`: "Sin asignar: el servicio de cobertura no respondió". Elena Ruiz lo asigna a mano | `docker compose start servicio-cobertura` |
| Broker caído | `docker compose stop rabbitmq` y crear un reclamo | El alta anda. El mensaje queda `PENDIENTE` en `mensajes_salientes` (consulta en el instructivo) | `docker compose start rabbitmq`: en segundos pasa a `PUBLICADO` y llega el correo |
| Consumidor caído | `docker compose stop servicio-notificaciones` y crear un reclamo | En RabbitMQ, `notificaciones.email` acumula mensajes listos | `docker compose start servicio-notificaciones`: los procesa y llegan los correos |
| Correo caído | `docker compose stop mailpit` y crear un reclamo | Tres intentos en `docker compose logs servicio-notificaciones`; el mensaje termina en `notificaciones.email.dlq` | `docker compose start mailpit`. El mensaje de la cola de errores queda para revisarlo a mano |

## Matriz de fallos

Qué hace el sistema cuando falla cada pieza. La regla general: **el reclamo se crea siempre**, salvo que falle
la base de datos.

| Pieza que falla | Qué hace el sistema | Qué ve el usuario | Recuperación | Test que lo cubre |
| --- | --- | --- | --- | --- |
| Georef caído, lento o sin Internet | Corta a los 3 s y responde `disponible: false` | "El mapa no responde en este momento"; el reclamo se crea con la ubicación pendiente | Automática en la próxima búsqueda | `UbicacionesIntegrationTest.georefCaidoDevuelveNoDisponibleSinError` |
| Georef no encuentra la dirección | Lista vacía | "No encontramos esa dirección"; se crea igual | — | `UbicacionesIntegrationTest.direccionInexistenteDevuelveListaVacia` |
| Servicio de IA caído o sin clave | Respaldo: prioridad de la categoría, origen `RESPALDO` | "por categoría (la IA no respondió)" | Automática al volver | `PrioridadPorIAIntegrationTest.siLaIaFallaElReclamoSeCreaConLaPrioridadDeLaCategoria` |
| Servicio de IA lento | Corta a los 6 s y usa el respaldo; con varias fallas seguidas el circuit breaker deja de llamarlo un rato | Igual que caído | El circuit breaker prueba de nuevo solo | `PrioridadPorIAIntegrationTest.siLaIaTardaElAltaNoEsperaMasQueElTiempoLimite`, `ClienteServicioPrioridadTest.despuesDeVariasFallasElCircuitoSeAbreYNoVuelveALlamar` |
| Gemini sin cuota (429) | El servicio responde 503 y el backend usa el respaldo | Igual que caído | Esperar a que se renueve la cuota, o `PRIORIDAD_IA_PROVEEDOR=simulado` | `GeminiAdapterTest.cuotaAgotadaEsProveedorNoDisponible` |
| Servicio SOAP caído o lento | Corta a los 2 s; el reclamo queda `INGRESADO` con la asignación pendiente | "Sin asignar: el servicio de cobertura no respondió" | La administradora lo asigna a mano | `AsignacionPorSoapIntegrationTest.servicioLentoDejaLaAsignacionPendienteSinTrabarElAlta` |
| SOAP sin áreas para el pedido | El reclamo queda `INGRESADO`, como en el Hito 1 | "Sin asignar" | Asignación manual | `AsignacionPorSoapIntegrationTest.listaVaciaDejaElReclamoIngresadoSinMarcarlo` |
| RabbitMQ caído | El mensaje queda `PENDIENTE` en la outbox; el publicador reintenta cada 2 s | Nada: el aviso interno ya está | Automática: sale cuando vuelve | `OutboxIntegrationTest.conElBrokerCaidoLosMensajesEsperanYSalenCuandoVuelve` |
| Servicio de notificaciones caído | Los mensajes esperan en la cola durable | Nada; el correo llega tarde | Automática al levantarlo | Cola durable en `TopologiaRabbitTest` |
| SMTP caído | Tres intentos (1 s y 2 s de espera) y después a la cola de errores | Nada; ese correo no llega | Revisar la cola de errores a mano | `ConfiguracionReintentosTest`, `FlujoCorreoIntegrationTest` |
| Mensaje repetido | El consumidor lo descarta por su `eventId` | Un solo correo | — | `ProcesadorAvisosTest.unMensajeRepetidoNoSeVuelveAMandar` |
| Mensaje inválido | Va directo a la cola de errores, sin reintentos | Nada | Revisarlo a mano | `FlujoCorreoIntegrationTest.unMensajeInvalidoTerminaEnLaColaDeErroresSinReintentos` |
| Telegram caído o sin Internet (modo `telegram`) | El bot registra el error y vuelve a consultar cada 2 s, desde la misma actualización | El código sigue vigente 10 minutos; la pantalla espera la confirmación | Automática al volver. Plan B: `RECLAMOS_TELEGRAM_MODO=simulado` y reiniciar el backend | `BotTelegramTest.siTelegramFallaNoSeCaeYVuelveAPedirDesdeElMismoPunto` |
| MySQL caído | Sin respaldo: es la base del sistema. Las operaciones responden error | Mensaje de error en la interfaz | `docker compose start mysql`; los datos están en el volumen | — |

Los tiempos límite suman como máximo unos 8 segundos dentro del alta (6 de la IA y 2 del SOAP). Georef se
consulta antes, desde la búsqueda, fuera del alta.

## Verificación automática

`scripts/verificar-demo.mjs` hace el recorrido de los pasos 2 a 7 sin interfaz y dice qué anda:

```text
docker compose run --rm verificar-demo
```

Corre en un contenedor de Node, así que no hace falta instalar nada. Con Node 20 o posterior también se puede
correr desde la máquina: `node scripts/verificar-demo.mjs`.

| Paso | Qué comprueba |
| --- | --- |
| Frontend, backend, cobertura, prioridad, RabbitMQ y Mailpit | Que cada contenedor responda: la interfaz y el proxy de `/api`, los contratos OpenAPI, el WSDL, el exchange con su cola y su consumidor, y el buzón |
| Login | Que Ana Pérez entre con `demo1234`: toma la cookie `XSRF-TOKEN`, la devuelve en `X-XSRF-TOKEN` y guarda la cookie de sesión, como el navegador. Además, que tenga el teléfono verificado y la cuenta sin bloquear, porque si no el alta da 403 |
| Georef | Que el backend devuelva sugerencias para Belgrano 450, en Bernal. Sin Internet es un **aviso**, no una falla |
| Alta del reclamo | `POST /api/reclamos` con la sesión de Ana Pérez y la sugerencia elegida: responde 201 |
| Prioridad con IA | Que el origen sea `IA`, o `CATEGORIA` si la IA sugirió menos. `RESPALDO` es una falla: la IA no contestó |
| Área por SOAP | Que quede asignado a un área y sin la asignación pendiente |
| Ubicación | Que quede `CONFIRMADA` con las coordenadas elegidas |
| Correo por RabbitMQ | Que en 30 segundos llegue a Mailpit al menos un correo de ese reclamo para ana@example.org |
| Cola de errores | Que `notificaciones.email.dlq` esté vacía; si no, es un aviso |

Termina con `N pasos bien, N avisos, N fallas` y sale con código 1 si hay alguna falla. El CI lo corre en el
trabajo «Demo completa», después de levantar los ocho contenedores con el proveedor simulado de IA, porque el
CI no tiene la clave de Gemini.

## Si algo sale mal

| Síntoma | Causa probable | Qué hacer |
| --- | --- | --- |
| `port is already allocated` al levantar | Hay un backend, frontend o MySQL corriendo en la máquina | Cerrarlo, o cambiar `DB_PORT` en `.env` si es MySQL |
| El backend nunca llega a `healthy` | Falta memoria, o MySQL no arrancó | `docker compose logs backend`; subir la memoria de Docker Desktop |
| Prioridad "por categoría (la IA no respondió)" | Falta `GEMINI_API_KEY`, la clave es inválida o se agotó la cuota | `docker compose logs servicio-prioridad`. Plan B: `PRIORIDAD_IA_PROVEEDOR=simulado` en `.env` y `docker compose up -d servicio-prioridad`; el motivo aclara que no es IA |
| "El mapa no responde" | La computadora no tiene Internet o Georef está caído | Mostrar el alta sin ubicación: es el comportamiento previsto |
| El alta responde `429 LIMITE_EXCEDIDO` | Se crearon más de `RECLAMOS_USO_RECLAMOS_POR_DIA` reclamos con la misma cuenta en 24 horas (20 en Compose) | Subir el valor en `.env` y `docker compose up -d backend`, o usar a Bruno Díaz |
| El bot de Telegram no contesta | Falta el token o el modo sigue en `simulado`, o la computadora no tiene Internet | `docker compose logs backend`. Plan B: modo `simulado` |
| No llegan correos | El consumidor o Mailpit no están levantados | `docker compose ps` y `docker compose logs servicio-notificaciones` |
