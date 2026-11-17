# Guion de la demo

La demo recorre **un solo reclamo** a través de las cinco integraciones del alta: Georef, la IA, el servicio
SOAP, RabbitMQ y el correo. La sexta, Telegram, se usa antes, una sola vez por cuenta, para verificar el
teléfono. Así se ve el flujo completo y no una captura suelta de cada pieza. Después se apagan servicios a propósito, para
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
| 2 | En la interfaz, entrar como **Ana Pérez** (`ana@example.org`, contraseña `demo1234`). **Crear reclamo**: Luminaria rota, dirección "Belgrano 450" | La pantalla de ingreso; después, Ana arriba a la derecha con **Salir** | Login con sesión del servidor (Spring Security) |
| 3 | Tocar **Buscar en el mapa** | Varias sugerencias, porque Belgrano 450 existe en varios municipios; cada una dice de cuál es y tiene "Ver en el mapa" | **API externa** (Georef), una búsqueda por municipio en paralelo; Adapter y proxy de caché |
| 4 | Elegir la sugerencia de Bernal (Quilmes): el formulario muestra "Municipio: Quilmes". Descripción: "Hay un cable colgando del poste de luz y chispea cuando llueve, justo frente a la escuela". Elegir una foto (JPEG o PNG) | La vista previa de la foto. Antes, probar con un `.txt` o un GIF: "La foto tiene que ser JPEG o PNG" | Foto obligatoria |
| 5 | **Crear reclamo** | Detalle: la foto, prioridad alta o crítica "sugerida por IA" con su motivo, área Alumbrado, dirección normalizada y **Ver en Google Maps** | **IA** (REST 2 con Gemini) y **SOAP** (cobertura). La foto no viaja a la IA |
| 6 | Pestaña Mailpit | Los correos "lo recibimos" y "ya tiene un área a cargo" para Ana, y el aviso para Carla Gómez | **Mensajería**: outbox, RabbitMQ y consumidor |
| 7 | Pestaña RabbitMQ: **Exchanges** → `reclamos.eventos`, **Queues** → `notificaciones.email` | El gráfico de mensajes publicados y entregados; `notificaciones.email.dlq` vacía | Publicar y suscribir, cola de errores |
| 8 | Pestañas del WSDL y de los dos Swagger | `ConsultarCobertura`, `POST /api/prioridades/evaluar` y los endpoints de REST 1 | **Contratos**: WSDL y OpenAPI |
| 9 | Como **Bruno Díaz**, crear un reclamo de **Otro problema** ("Cable suelto") en Belgrano 450, eligiendo la sugerencia de Bernal (Quilmes), con la descripción "El poste de luz frente a la escuela tiene un cable colgando que tira chispas" | Antes de crear aparece **¿Es alguno de estos?**: "La IA cree que el reclamo REC-… es el mismo problema", con el reclamo de Ana marcado aunque sea de otra categoría, su foto y sin su nombre. **Es el mismo: asociarme** vuelve a **Mis reclamos** con el aviso; Ana ve "1 vecino apoya este reclamo". Cuando Carla lo toma (paso 10), Bruno recibe "El reclamo … que apoyaste paso de ASIGNADO a EN_PROCESO" | **IA** que compara textos (PR 27), duplicados (Haversine) y avisos a los asociados |
| 10 | **Salir** y entrar como **Carla Gómez** (`carla@example.org`, agente de Alumbrado): ver la foto, tomar el reclamo y resolverlo | La agente ve la foto; el historial suma dos pasos | Cada rol ve y hace lo suyo: lo decide el dominio con el usuario de la sesión |
| 11 | Volver a Mailpit | "cambió de estado" y "está resuelto" para Ana | El mismo flujo de mensajería, otro evento |
| 12 | Entrar como Ana, abrir el reclamo resuelto y **Reabrir** con el motivo "Sigue apagado". Que Carla lo resuelva otra vez | La segunda vez, el botón **Reabrir** ya no aparece: solo **Confirmar solución** | Reaperturas con límite (dominio) |
| 13 | **Crear una cuenta** nueva e ir a **Crear reclamo** | Pide verificar el teléfono. Con el bot: código, enlace a Telegram y "Compartir mi teléfono". Sin bot: el número en un formulario. Repetir con el número de otra cuenta: "Ese número ya está verificado en otra cuenta" | **Segunda API externa** (Telegram), Adapter |
| 14 | Entrar como **Elena Ruiz** → **Administración** → **Cuentas de ciudadanos** → **Bloquear** la cuenta nueva | Esa cuenta entra y ve sus reclamos, pero **Crear reclamo** muestra el aviso de cuenta bloqueada | Chain of Responsibility de los controles de uso |
| 15 | Como Ana, **Crear reclamo** con la categoría **Otro problema**: en "¿Qué tipo de problema es?", "Baldosas levantadas", y en la descripción "Hay baldosas sueltas en la vereda de la escuela". Después, como Elena, abrir el reclamo | El reclamo queda sin área. En unos segundos, el detalle de Elena muestra "La IA sugiere Vereda rota: …" con la categoría ya elegida. **Recategorizar**: pasa a Vereda rota, se asigna solo a Obras Públicas y Ana recibe el aviso | **IA** que sugiere y un administrador que decide (PR 26); cola `reclamos.categoria` |
| 16 | Como Elena, pestaña **Tablero** | Tablero de gestión: pendientes, vencidos con su porcentaje, sin asignar y la tabla por área y municipio. Debajo, el público con filtros. Salir y, en la pantalla de ingreso, **Ver el tablero público**: las mismas cifras sin sesión | Lado de lectura separado (Domain Reporting, Clase 5) |

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
| Servicio de IA caído o sin clave | Prioridad de la categoría, origen `PENDIENTE_IA`, y pedido de revisión por la cola. Reintenta cada 20 s; después de 4 intentos queda `RESPALDO` | "por categoría (la IA lo está revisando)" y después "por categoría (la IA no respondió)" | Automática: si vuelve dentro del minuto, la IA completa la prioridad | `ReevaluacionPrioridadIntegrationTest.siLaIaSigueSinResponderReintentaYDespuesQuedaLaCategoria`, `PrioridadPorIAIntegrationTest.siLaIaFallaElReclamoSeCreaConLaPrioridadDeLaCategoria` (sin mensajería) |
| Servicio de IA lento | El alta corta a los 4 s y deja el reclamo `PENDIENTE_IA`; la revisión por la cola espera hasta 10 s. Con varias fallas seguidas el circuit breaker deja de llamarlo un rato | "por categoría (la IA lo está revisando)"; el detalle se actualiza solo cuando la IA responde, y llega un aviso si la prioridad subió | El circuit breaker prueba de nuevo solo | `PrioridadPorIAIntegrationTest.siLaIaTardaElAltaNoEsperaMasQueElTiempoLimite`, `ReevaluacionPrioridadIntegrationTest.cuandoLaIaRespondeSubeLaPrioridadYAvisaAlVecinoYAlArea`, `ClienteServicioPrioridadTest.despuesDeVariasFallasElCircuitoSeAbreYNoVuelveALlamar` |
| Gemini sin cuota (429) | El servicio responde 503: el reclamo queda `PENDIENTE_IA` y, si la cuota no vuelve en el minuto de reintentos, `RESPALDO` | Igual que caído | Esperar a que se renueve la cuota, o `PRIORIDAD_IA_PROVEEDOR=simulado` | `GeminiAdapterTest.cuotaAgotadaEsProveedorNoDisponible` |
| Servicio SOAP caído o lento | Corta a los 2 s; el reclamo queda `INGRESADO` con la asignación pendiente | "Sin asignar: el servicio de cobertura no respondió" | La administradora lo asigna a mano | `AsignacionPorSoapIntegrationTest.servicioLentoDejaLaAsignacionPendienteSinTrabarElAlta` |
| SOAP sin áreas para el pedido | El reclamo queda `INGRESADO`, como en el Hito 1 | "Sin asignar" | Asignación manual | `AsignacionPorSoapIntegrationTest.listaVaciaDejaElReclamoIngresadoSinMarcarlo` |
| RabbitMQ caído | El mensaje queda `PENDIENTE` en la outbox; el publicador reintenta cada 2 s | Nada: el aviso interno ya está | Automática: sale cuando vuelve | `OutboxIntegrationTest.conElBrokerCaidoLosMensajesEsperanYSalenCuandoVuelve` |
| Servicio de notificaciones caído | Los mensajes esperan en la cola durable | Nada; el correo llega tarde | Automática al levantarlo | Cola durable en `TopologiaRabbitTest` |
| SMTP caído | Tres intentos (1 s y 2 s de espera) y después a la cola de errores | Nada; ese correo no llega | Revisar la cola de errores a mano | `ConfiguracionReintentosTest`, `FlujoCorreoIntegrationTest` |
| Mensaje repetido | El consumidor lo descarta por su `eventId` | Un solo correo | — | `ProcesadorAvisosTest.unMensajeRepetidoNoSeVuelveAMandar` |
| Mensaje inválido | Va directo a la cola de errores, sin reintentos | Nada | Revisarlo a mano | `FlujoCorreoIntegrationTest.unMensajeInvalidoTerminaEnLaColaDeErroresSinReintentos` |
| Pedido de revisión de prioridad repetido o inválido | Repetido: se confirma sin llamar a la IA, porque el reclamo ya no está pendiente. Inválido: a `reclamos.prioridad.dlq` | Nada | — | `ReevaluacionPrioridadIntegrationTest.unPedidoRepetidoNoVuelveALlamarNiAvisa`, `ListenerReevaluacionPrioridadTest.unPedidoIlegibleOConOtraVersionVaALaColaDeErrores` |
| Telegram caído o sin Internet (modo `telegram`) | El bot registra el error y vuelve a consultar cada 2 s, desde la misma actualización | El código sigue vigente 10 minutos; la pantalla espera la confirmación | Automática al volver. Plan B: `RECLAMOS_TELEGRAM_MODO=simulado` y reiniciar el backend | `BotTelegramTest.siTelegramFallaNoSeCaeYVuelveAPedirDesdeElMismoPunto` |
| Disco del volumen de fotos lleno o sin permisos | La foto no se puede guardar: el alta responde `500` y no se crea el reclamo. Si falla después del alta (al confirmar), el reclamo queda creado sin foto visible y el error queda en el log | Error al crear, o "Este reclamo no tiene foto" | Liberar espacio en el volumen `reclamos-fotos` | `AlmacenFotosLocalTest`, `GestionReclamosFacadeTest.siElAltaFallaLaFotoPendienteSeDescartaYElErrorSigue` |
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
| Georef | Que el backend devuelva sugerencias para Belgrano 450 y que una sea de Quilmes. Sin Internet es un **aviso**, no una falla |
| Alta del reclamo | `POST /api/reclamos` multipart con la sesión de Ana Pérez, la sugerencia elegida y una foto JPEG chica que viene en el script: responde 201 |
| Foto | Que `GET /api/reclamos/{numero}/foto` devuelva un JPEG con la sesión de Ana |
| Tablero público | Que `GET /api/dashboard/publico` cuente al menos el reclamo recién creado |
| Prioridad con IA | Que el origen sea `IA`, o `CATEGORIA` si la IA sugirió menos. Si el alta quedó `PENDIENTE_IA`, el script espera hasta 90 s la revisión por la cola. `RESPALDO` es una falla: la IA no contestó ni en los reintentos |
| Área por SOAP | Que quede asignado a un área y sin la asignación pendiente |
| Ubicación | Que quede `CONFIRMADA` con las coordenadas elegidas |
| Correo por RabbitMQ | Que en 30 segundos llegue a Mailpit al menos un correo de ese reclamo para ana@example.org |
| Cola de errores | Que `notificaciones.email.dlq` esté vacía; si no, es un aviso |

Termina con `N pasos bien, N avisos, N fallas` y sale con código 1 si hay alguna falla. El CI lo corre en el
trabajo «Demo completa», después de levantar los ocho contenedores con el proveedor simulado de IA, porque el
CI no tiene la clave de Gemini.

Después, el mismo trabajo corre la [prueba de carga básica](requerimientos.md#prueba-de-carga): si la
demo responde con errores cuando la consultan 20 usuarios a la vez, el CI queda en rojo.

## Si algo sale mal

| Síntoma | Causa probable | Qué hacer |
| --- | --- | --- |
| `port is already allocated` al levantar | Hay un backend, frontend o MySQL corriendo en la máquina | Cerrarlo, o cambiar `DB_PORT` en `.env` si es MySQL |
| El backend nunca llega a `healthy` | Falta memoria, o MySQL no arrancó | `docker compose logs backend`; subir la memoria de Docker Desktop |
| Prioridad "por categoría (la IA no respondió)" | Falta `GEMINI_API_KEY`, la clave es inválida o se agotó la cuota | `docker compose logs servicio-prioridad`. Plan B: `PRIORIDAD_IA_PROVEEDOR=simulado` en `.env` y `docker compose up -d servicio-prioridad`; el motivo aclara que no es IA |
| "El mapa no responde" | La computadora no tiene Internet o Georef está caído | Mostrar el alta sin ubicación: es el comportamiento previsto |
| El alta responde `413` | La foto pesa más de 5 MB o mide más de 4000 píxeles de lado | Sacarla con menos resolución o recortarla |
| El alta responde `429 LIMITE_EXCEDIDO` | Se crearon más de `RECLAMOS_USO_RECLAMOS_POR_DIA` reclamos con la misma cuenta en 24 horas (20 en Compose) | Subir el valor en `.env` y `docker compose up -d backend`, o usar a Bruno Díaz |
| El bot de Telegram no contesta | Falta el token o el modo sigue en `simulado`, o la computadora no tiene Internet | `docker compose logs backend`. Plan B: modo `simulado` |
| No llegan correos | El consumidor o Mailpit no están levantados | `docker compose ps` y `docker compose logs servicio-notificaciones` |
