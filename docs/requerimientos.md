# Requerimientos

Requerimientos funcionales y no funcionales del sistema, incluidos los del componente de IA. Cada uno dice
cómo se verifica: el caso de prueba de las [evidencias](evidencias/README.md) (CP), el test automático o el
documento donde está la decisión.

## Actores

| Actor | Quién es | Qué hace |
| --- | --- | --- |
| Visitante | Cualquiera, sin sesión | Ve el tablero público y crea una cuenta |
| Ciudadano | Vecino registrado | Crea reclamos con foto y ubicación, apoya reclamos de otros, sigue los suyos, confirma la solución o reabre |
| Agente | Empleado de un área municipal | Toma, resuelve o rechaza los reclamos de su área y ve el tablero de gestión de su área |
| Administrador | Empleado de la administración | Ve todos los reclamos, asigna los que quedaron sin área, bloquea cuentas y ve el tablero de gestión completo |
| Sistemas externos | Georef, Gemini, Telegram, el servidor de correo | Ubican direcciones, sugieren la prioridad, verifican el teléfono y reciben los avisos |

## Requerimientos funcionales

| Id | Requerimiento | Se verifica con |
| --- | --- | --- |
| RF-01 | Un visitante crea una cuenta de ciudadano con nombre, apellido, email, contraseña de al menos 8 caracteres y, si quiere, DNI. Email y DNI no se repiten | CP45 |
| RF-02 | Los usuarios entran con email y contraseña, y salen. Un error de login no revela si la cuenta existe | CP41, CP42, CP46 |
| RF-03 | Un ciudadano verifica su teléfono por Telegram (o con la verificación simulada, cuando no hay bot configurado) antes de crear reclamos. Un número vale para una sola cuenta | CP47, CP48, CP49 |
| RF-04 | Un ciudadano crea un reclamo con categoría, barrio, descripción, foto obligatoria y, si quiere, la dirección ubicada en el mapa | CP01 a CP03, CP27 a CP31, CP53, CP54 |
| RF-05 | Antes de crear el reclamo, el sistema muestra los reclamos abiertos de la misma categoría a menos de 50 m, sin datos del autor, y el ciudadano puede apoyar uno en vez de crear otro | CP58 a CP62 |
| RF-06 | El sistema asigna el reclamo al área que cubre ese barrio y esa categoría. Si no hay ninguna, o el servicio de cobertura no responde, queda sin asignar para la administración | CP05, CP06, CP17 a CP20 |
| RF-07 | El sistema calcula la prioridad con la IA a partir de la descripción, sin bajar nunca la prioridad de la categoría. Si la IA no responde, usa la de la categoría | CP04, CP21 a CP26 |
| RF-08 | Los agentes toman, resuelven o rechazan los reclamos de su área. El ciudadano confirma la solución o reabre el reclamo con un motivo, una sola vez y dentro de 7 días | CP08 a CP10, CP52 |
| RF-09 | Cada cambio queda en el historial, y el ciudadano y el área reciben un aviso en la aplicación y por correo | CP07, CP11, CP14, CP32, CP33 |
| RF-10 | El sistema marca como vencidos los reclamos que pasaron su fecha límite, les sube la prioridad y avisa | CP13 |
| RF-11 | Cada rol ve solo los reclamos que le tocan: el ciudadano los suyos, el agente los de su área, el administrador todos | CP12, CP43, CP55 |
| RF-12 | El administrador asigna o reasigna el área de un reclamo y bloquea o desbloquea cuentas de ciudadanos | CP06, CP51 |
| RF-13 | Cualquiera, sin sesión, ve un tablero público con cifras agregadas por período, barrio y categoría | CP63, CP64, CP67 |
| RF-14 | Agentes y administradores ven un tablero de gestión con pendientes, vencidos y sin asignar, por área y barrio | CP65, CP66 |

## Requerimientos no funcionales

| Id | Atributo | Requerimiento | Se verifica con |
| --- | --- | --- | --- |
| RNF-1 | Disponibilidad | El reclamo se crea aunque fallen Georef, la IA, el servicio SOAP, RabbitMQ, el servicio de notificaciones o el correo. Cada falla tiene su respaldo y se recupera sola o con un paso manual documentado. Solo la base de datos no tiene respaldo | [Matriz de fallos](demo.md#matriz-de-fallos), CP20, CP24, CP30, CP35, CP36, CP39 |
| RNF-2 | Tiempo de respuesta del alta | Una integración lenta no traba el alta más que su tiempo límite: 6 s la IA y 2 s el SOAP, unos 8 s en el peor caso. Georef se consulta antes, en la búsqueda, con 3 s. Con el circuito de la IA abierto, el respaldo es inmediato | `PrioridadPorIAIntegrationTest.siLaIaTardaElAltaNoEsperaMasQueElTiempoLimite`, `AsignacionPorSoapIntegrationTest.servicioLentoDejaLaAsignacionPendienteSinTrabarElAlta`, `ClienteServicioPrioridadTest.despuesDeVariasFallasElCircuitoSeAbreYNoVuelveALlamar` |
| RNF-3 | Carga básica | Con la demo levantada en una notebook, 20 usuarios simultáneos consultando durante 30 s (catálogos, tablero público, listado, detalle y avisos): ningún error y percentil 95 de 1000 ms o menos | CP68, `scripts/prueba-carga.mjs` (ver [Prueba de carga](#prueba-de-carga)) |
| RNF-4 | Seguridad | Sesión del servidor con cookie `HttpOnly` y `SameSite=Lax`, que vence a los 30 minutos sin actividad. Token CSRF en todo `POST`, `PATCH` y `DELETE`. Contraseñas con BCrypt. Permisos por rol en cada endpoint, no solo en la interfaz | CP09, CP41 a CP46 |
| RNF-5 | Privacidad de los datos personales | El teléfono se muestra enmascarado y no viaja a la IA ni a RabbitMQ. Los mensajes de la cola no llevan DNI, teléfono ni descripción. La foto se guarda re-codificada, sin EXIF. Los posibles duplicados y los tableros no muestran quién reclamó | CP53, CP58, CP63, `OutboxIntegrationTest`, [contratos de mensajes](contratos/README.md) |
| RNF-6 | Abuso | Hasta 5 reclamos por día y 3 códigos de verificación por hora por ciudadano, con `429`. Un apoyo por ciudadano y reclamo, aun con dos pedidos simultáneos | CP50, CP61 |
| RNF-7 | Consistencia | Bloqueo optimista en los reclamos. El aviso y el reclamo se guardan en la misma transacción (outbox). Si el alta falla, no queda ni el mensaje ni la foto | CP32, CP56, [Arquitectura](arquitectura.md) |
| RNF-8 | Entrega de los avisos | Al menos una vez, sin correos repetidos: el consumidor descarta por `eventId`. Tres reintentos y después a la cola de errores; un mensaje inválido va directo a la cola de errores | CP34, CP36 |
| RNF-9 | Manejo de errores y logging | Todos los errores de la API responden con el mismo formato (`status`, `codigo`, `mensaje`, `detalles` por campo, `ruta` y `fecha`) y el código HTTP que corresponde. Los eventos de negocio quedan en el logger `auditoria`; las fallas de las integraciones, en el log de cada servicio | [Errores en el contrato](api-contract.md#errores), CP03, CP07 |
| RNF-10 | Configuración externalizada | Estrategias, tiempos límite, límites de uso y modo de Telegram se cambian por propiedades o variables de entorno, sin tocar código. Las claves (`GEMINI_API_KEY`, `OPENAI_API_KEY`, `TELEGRAM_BOT_TOKEN`) solo van en `.env`, que no está en Git | CP04, CP05, `ConfiguracionAplicacionTest`, [Despliegue](despliegue.md) |
| RNF-11 | Ejecución en laboratorio | `docker compose up --build` levanta los ocho contenedores en orden, con chequeos de salud, y entran en la memoria de una notebook | CP37, CP40 |
| RNF-12 | Mantenibilidad | Cada integración está detrás de una interfaz con su adaptador, y la regla de negocio no cambia si cambia el proveedor. El CI corre todos los tests y la demo completa en cada cambio | [Patrones](patrones.md), [CI](../.github/workflows/ci.yml) |

### Prueba de carga

`scripts/prueba-carga.mjs` simula usuarios simultáneos que recorren, en bucle, los endpoints de lectura
más usados:

- `GET /api/categorias`
- `GET /api/dashboard/publico`
- `GET /api/reclamos`
- `GET /api/notificaciones`
- `GET /api/reclamos/{numero}`

Inicia sesión como Ana Pérez y **no crea reclamos**, así no choca con el límite diario ni ensucia la base.
Mide el percentil 50, el percentil 95 y el máximo de cada endpoint, y cuenta los errores por causa. Sale con
código 1 si no cumple RNF-3. Solo usa módulos de Node: no necesita `npm install`.

```bash
# Con la demo levantada. Sin Node en la máquina, desde un contenedor:
docker compose run --rm -T prueba-carga > docs/evidencias/prueba-carga.md

# Con Node 22:
node scripts/prueba-carga.mjs --usuarios 20 --duracion 30 --salida docs/evidencias/prueba-carga.md
```

| Opción | Variable de entorno | Por defecto |
| --- | --- | --- |
| `--url` | `BACKEND_URL` | `http://localhost:8080` |
| `--usuarios` | `CARGA_USUARIOS` | 20 |
| `--duracion` (segundos) | `CARGA_DURACION_S` | 30 |
| `--p95-maximo` (ms) | `CARGA_P95_MAXIMO_MS` | 1000 |
| `--salida` | — | Solo la consola |

El trabajo «Demo completa» del CI la corre después de recorrer la demo, con 20 usuarios durante 20 s, y
publica el informe en el resumen del trabajo y como artefacto `prueba-carga`. Ahí la máquina es chica y
comparte la CPU con los ocho contenedores, así que el límite del percentil 95 es de 3000 ms. Lo que no se
afloja son los errores: tienen que ser cero.

**Qué no mide.** No mide el alta: cada alta llama a la IA, que tiene su propio límite por minuto en el nivel
gratuito de Gemini, y lo que tarda el alta depende de los tiempos límite de RNF-2. Tampoco es una prueba de
estrés: no busca el punto en que el sistema deja de responder.

## Requerimientos del componente de IA

El componente de IA es el `servicio-prioridad` (REST 2): sugiere la prioridad de un reclamo a partir de su
categoría y su descripción. Las metas de exactitud y latencia se miden con
[`EvaluarPrioridades.java`](../servicios/servicio-prioridad/evaluacion/EvaluarPrioridades.java) sobre los 25
casos de [`casos.tsv`](../servicios/servicio-prioridad/evaluacion/casos.tsv), con la prioridad que el grupo
espera para cada uno. El programa escribe [ia-evaluacion.md](evidencias/ia-evaluacion.md) con una tabla
de metas que dice si cada una se cumple.

| Id | Atributo | Requerimiento | Se verifica con |
| --- | --- | --- | --- |
| RNF-IA-1 | Exactitud mínima | Al menos el 70 % de los 25 casos con la prioridad esperada exacta, y al menos el 90 % a un nivel o menos de la esperada (por ejemplo, ALTA cuando se esperaba CRITICA) | `EvaluarPrioridades.java`, CP25 |
| RNF-IA-2 | Latencia | Percentil 95 del tiempo de respuesta de REST 2 de 6000 ms o menos, que es el tiempo límite del backend. Si la IA tarda más, el backend corta y usa la prioridad de la categoría; si falla la mitad de las últimas 5 llamadas, el circuito deja de llamarla durante 30 s | `EvaluarPrioridades.java`, CP24, `siLaIaTardaElAltaNoEsperaMasQueElTiempoLimite` |
| RNF-IA-3 | Disponibilidad | Al menos el 95 % de los casos con una respuesta válida (`200` con una de las cuatro prioridades). Una respuesta fuera del esquema es un error del proveedor (`502`) y el backend usa el respaldo | `EvaluarPrioridades.java`, `EvaluadorPrioridadTest`, `ErroresDelProveedorTest` |
| RNF-IA-4 | Privacidad de los datos | Al proveedor de IA solo le llegan la categoría y la descripción: nunca nombre, DNI, email, teléfono, dirección ni foto. El motivo que devuelve no incluye datos personales. La clave del proveedor solo está en `.env` | `PrioridadPorIAIntegrationTest.alServicioDeIaSoloLeLlegaLaCategoriaYLaDescripcion`, `ConfiguracionProveedorTest` |
| RNF-IA-5 | Robustez | Una instrucción escondida en la descripción no cambia la prioridad. La IA puede subir la prioridad de la categoría pero no bajarla, así que un error de la IA no deja sin atender un reclamo peligroso | CP26 (caso 25 de la evaluación), `InstruccionesPrioridadTest`, `PrioridadPorIATest` |

**Sobre la privacidad en el nivel gratuito.** En el nivel gratuito de Gemini, Google puede usar lo que se le
manda para mejorar sus productos. Por eso RNF-IA-4 no depende del proveedor: el contrato de REST 2 solo
acepta categoría y descripción, y el backend no tiene forma de mandarle otra cosa. Aun así, un vecino podría
escribir un dato personal en la descripción; las instrucciones le piden al modelo que no lo repita en el
motivo, y en la demo se usan reclamos de prueba. Para datos reales, el adaptador de OpenAI con una cuenta
paga, o un modelo propio, son cambios de configuración.

**Si una meta no se cumple.** Las metas son la propuesta del grupo y se revisan con los resultados. Lo que se
puede ajustar sin tocar código:

- Los criterios de `instrucciones-prioridad.txt`, sobre todo si la IA pone sistemáticamente menos
  prioridad de la esperada.
- El modelo, con `GEMINI_MODELO`.
- El proveedor, con `PRIORIDAD_IA_PROVEEDOR`.

Un caso que la IA clasifica por debajo de lo esperado sigue con la prioridad de la categoría como piso, por
RNF-IA-5.

## Decisiones de diseño de la IA

| Decisión | Por qué | Dónde |
| --- | --- | --- |
| Clasificar la prioridad, y no la categoría | La categoría la elige el vecino y define el área. La prioridad es la que más gana con leer el texto libre | [Decisiones de la Segunda Parte](arquitectura.md#decisiones-de-la-segunda-parte) |
| Un servicio aparte (REST 2), no una llamada desde el backend | El backend no conoce a Gemini ni a OpenAI: cambiar de proveedor o de modelo no lo toca | [servicios/README.md](../servicios/README.md#servicio-de-prioridad-con-ia-rest-2) |
| Sincrónica dentro del alta, con tiempo límite y respaldo | El ciudadano ve la prioridad y el motivo al crear el reclamo. Una cola daría la prioridad más tarde y complicaría el flujo de estados | [Prioridad con IA](arquitectura.md#prioridad-con-ia) |
| Respuesta con esquema JSON y validada | El modelo solo puede contestar una de las cuatro prioridades y un motivo corto. Si contesta otra cosa, es un error y hay respaldo | `EsquemaRespuesta`, `EvaluadorPrioridad` |
| La IA sube, no baja | Un falso negativo (un peligro clasificado como menor) es más caro que un falso positivo | `PrioridadPorIA` |
| Gemini por defecto, OpenAI como segundo adaptador y un simulado para los tests | Gemini tiene nivel gratuito sin tarjeta. El simulado permite correr el CI y la demo sin clave, y dice en cada respuesta que no es IA | [Adapter, Factory y Decorator](patrones.md#adapter-factory-y-decorator-en-la-prioridad-con-ia) |
