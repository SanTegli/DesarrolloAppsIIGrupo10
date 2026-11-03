# Servicios de integración

Aplicaciones de la Segunda Parte que corren separadas del backend. Cada una es un proyecto Spring Boot
con su propio proceso y contenedor; no comparten código con `backend/`: se integran por contrato.

| Servicio | Carpeta | Protocolo | Puerto | PR | Estado |
| --- | --- | --- | --- | --- | --- |
| Cobertura | `servicio-cobertura/` | SOAP 1.1, WSDL en `/ws/cobertura.wsdl` | 8081 | 9 | Hecho |
| Prioridad con IA | `servicio-prioridad/` | REST 2, OpenAPI en `/swagger-ui.html` | 8082 | 10 | Hecho |
| Notificaciones | `servicio-notificaciones/` | Consume de RabbitMQ, envía SMTP a Mailpit | — | 12 | Hecho |

Los contratos están en [`docs/contratos/`](../docs/contratos/README.md).

## Cómo compilar y probar

Desde esta carpeta, con Java 21. No hace falta instalar Maven: la carpeta trae su propio wrapper.

```bash
./mvnw verify
```

En Windows, `.\mvnw.cmd verify`. Algunos tests leen archivos de `backend/` y `docs/`, así que se corren
desde el repositorio completo. La integración continua corre el mismo comando en cada pull request.

## Servicio de cobertura (SOAP)

Responde qué áreas municipales atienden una categoría en un barrio. Es el dueño de esa información: la
guarda en `servicio-cobertura/src/main/resources/cobertura.json`, con los mismos ids que la semilla del
backend.

```text
servicio-cobertura/
  src/main/resources/cobertura.xsd     Contrato. Las clases del pedido y la respuesta se generan de acá
  src/main/resources/cobertura.json    Catálogo: municipios, barrios, categorías y qué cubre cada área
  src/main/java/.../configuracion/     Publicación del WSDL, validación contra el XSD y carga del catálogo
  src/main/java/.../endpoint/          CoberturaEndpoint: la operación ConsultarCobertura
  src/main/java/.../catalogo/          CatalogoCobertura: la consulta, sin nada de SOAP
```

Para levantarlo, desde esta carpeta:

```bash
./mvnw -pl servicio-cobertura spring-boot:run
```

Queda escuchando en `http://localhost:8081`. El WSDL está en `http://localhost:8081/ws/cobertura.wsdl` y
[`docs/contratos/README.md`](../docs/contratos/README.md#soap-consultarcobertura) tiene una petición de
ejemplo para PowerShell.

Para que el backend lo use, se arranca el backend con `RECLAMOS_COBERTURA_ORIGEN=soap`. En
`docker compose up --build` ya viene así.

| Propiedad | Valor por defecto | Para qué |
| --- | --- | --- |
| `server.port` | `8081` | Puerto HTTP |
| `cobertura.catalogo` | `classpath:cobertura.json` | Archivo del catálogo; se puede cambiar sin recompilar, por ejemplo `COBERTURA_CATALOGO=file:/datos/cobertura.json` |

Un catálogo inconsistente (un área que cubre un barrio de otro municipio, ids repetidos, una categoría que
no existe) impide arrancar, en lugar de dar respuestas equivocadas después.

| Test | Qué comprueba |
| --- | --- |
| `CatalogoCoberturaTest` | Consultas válidas, lista vacía, ids desconocidos y catálogos inconsistentes |
| `CatalogoSincronizadoTest` | `cobertura.json` y `backend/.../datos-dev.sql` tienen los mismos ids y la misma cobertura |
| `CoberturaEndpointTest` | La operación completa con `MockWebServiceClient`: respuesta, validación contra el XSD y faults |
| `ServicioSoapHttpTest` | El servicio por HTTP real: WSDL publicado, consulta válida y fault con HTTP 500. Regenera `docs/contratos/soap/cobertura.wsdl` |
| `ContratoPublicadoTest` | El XSD del servicio y el de `docs/contratos/soap/` son idénticos |

## Servicio de prioridad con IA (REST 2)

Sugiere la prioridad de un reclamo a partir de su categoría y su descripción, con un modelo de lenguaje
externo. Es el componente de IA del sistema, publicado como servicio (*Model as a Service*): el backend no
habla con Gemini ni con OpenAI, habla con esta API.

```text
servicio-prioridad/
  src/main/resources/instrucciones-prioridad.txt   Criterios de cada prioridad que recibe el modelo
  src/main/java/.../api/                           POST /api/prioridades/evaluar y sus errores
  src/main/java/.../evaluacion/                    EvaluadorPrioridad: arma la consulta y valida la respuesta
  src/main/java/.../proveedor/                     ProveedorIA y sus adaptadores: Gemini, OpenAI y simulado
  src/main/java/.../configuracion/                 Elige el adaptador según la configuración (Factory)
  evaluacion/                                      Los 25 casos de prueba y el programa que los evalúa
```

### Proveedores

| `prioridad.ia.proveedor` | Qué usa | Necesita |
| --- | --- | --- |
| `gemini` (por defecto) | API de Gemini, modelo `gemini-3.5-flash-lite` | `GEMINI_API_KEY`. Es gratis y no pide tarjeta; se crea en [Google AI Studio](https://aistudio.google.com/apikey) |
| `openai` | API de OpenAI, modelo `gpt-5.4-mini` | `OPENAI_API_KEY`, con crédito cargado |
| `simulado` | Palabras clave, sin red | Nada. **No es IA**: lo dice en cada respuesta. Sirve para los tests y para trabajar sin clave |

Cambiar de proveedor es configuración: cada uno es un adaptador de la interfaz `ProveedorIA`, y el resto del
servicio no cambia. Sin clave, el servicio arranca igual y responde 503; el backend usa entonces la prioridad
de la categoría.

**Privacidad.** En el nivel gratuito de Gemini, Google puede usar lo que se le manda para mejorar sus
productos. El servicio solo recibe categoría y descripción, nunca datos personales, y en la demo se usan
reclamos de prueba.

### Cómo levantarlo

Con la clave en la terminal (PowerShell):

```powershell
$env:GEMINI_API_KEY="la-clave-de-ai-studio"
cd servicios
.\mvnw.cmd -pl servicio-prioridad spring-boot:run
```

Swagger UI queda en `http://localhost:8082/swagger-ui.html`. Para probar sin clave:
`$env:PRIORIDAD_IA_PROVEEDOR="simulado"`.

Para que el backend lo use, se arranca el backend con `RECLAMOS_PRIORIDAD_ESTRATEGIA=ia`. En
`docker compose up --build` ya viene así, y la clave se toma de `.env`.

| Propiedad | Valor por defecto | Para qué |
| --- | --- | --- |
| `server.port` | `8082` | Puerto HTTP |
| `prioridad.ia.proveedor` | `gemini` | `gemini`, `openai` o `simulado` |
| `prioridad.ia.tiempo-limite` | `5s` | Cuánto se espera al proveedor; después responde 504 |
| `prioridad.ia.gemini.modelo` | `gemini-3.5-flash-lite` | Modelo de Gemini; se cambia con `GEMINI_MODELO` |
| `prioridad.ia.openai.modelo` | `gpt-5.4-mini` | Modelo de OpenAI; se cambia con `OPENAI_MODELO` |
| `prioridad.ia.instrucciones` | `classpath:instrucciones-prioridad.txt` | Criterios para el modelo; se pueden ajustar sin tocar código |

### Evaluación con los 25 casos

`evaluacion/casos.tsv` tiene 25 reclamos de prueba con la prioridad que el grupo espera: seis críticos,
seis altos, siete medios y seis bajos. Uno intenta engañar al modelo con una instrucción escondida en la
descripción. Con el servicio levantado con la clave, desde la raíz del repositorio:

```powershell
java servicios/servicio-prioridad/evaluacion/EvaluarPrioridades.java
```

Manda los 25 casos con 7 segundos entre uno y otro, para respetar el límite por minuto del nivel gratuito,
y escribe [`docs/evidencias/ia-evaluacion.md`](../docs/evidencias/ia-evaluacion.md) con los aciertos, los
casos en que la IA puso menos prioridad de la esperada y los tiempos. Tarda unos tres minutos. No necesita
Maven: Java 21 ejecuta el archivo directamente.

| Test | Qué comprueba |
| --- | --- |
| `GeminiAdapterTest`, `OpenAiAdapterTest` | Cada adaptador contra la API simulada con `MockRestServiceServer`: formato del pedido, lectura de la respuesta, cuota agotada, clave rechazada, respuesta bloqueada y tiempo agotado. Nunca llaman al proveedor real |
| `EvaluadorPrioridadTest`, `InstruccionesPrioridadTest` | Validación de la respuesta del modelo y la defensa contra instrucciones escondidas en la descripción |
| `PrioridadControllerTest`, `ErroresDelProveedorTest` | El endpoint completo: 200, 400, 502, 503 y 504 |
| `ConfiguracionProveedorTest` | La Factory elige el adaptador por configuración y no muestra las claves |
| `OpenApiPrioridadTest` | El contrato documenta la operación y sus errores; regenera `docs/openapi/prioridad.yaml` |

## Servicio de notificaciones (RabbitMQ y correo)

Consume los avisos que publica el backend en RabbitMQ y los manda por correo. No tiene API HTTP: solo escucha
la cola `notificaciones.email`. El contrato de los mensajes está en
[`docs/contratos/mensajes.md`](../docs/contratos/mensajes.md).

```text
servicio-notificaciones/
  src/main/java/.../configuracion/TopologiaRabbit.java       Exchanges, cola y cola de errores
  src/main/java/.../configuracion/ConfiguracionReintentos.java  Qué se reintenta y qué va directo a errores
  src/main/java/.../aviso/ListenerAvisos.java                 @RabbitListener de la cola
  src/main/java/.../aviso/ProcesadorAvisos.java               Lee, descarta repetidos, manda y registra
  src/main/java/.../aviso/CorreoSmtp.java                     El correo, por SMTP
  src/main/resources/schema.sql                               Tabla de eventId ya procesados
```

| Qué pasa | Cómo |
| --- | --- |
| Llega un aviso | Se valida contra el contrato, se manda el correo y se registra el `eventId` |
| Llega un repetido | El `eventId` ya está registrado: se confirma sin mandar otro correo |
| El SMTP no responde | Tres intentos en total, esperando 1 y 2 segundos; después, a `notificaciones.email.dlq` |
| Llega algo que no cumple el contrato | Directo a `notificaciones.email.dlq`, sin reintentos |
| RabbitMQ se cae | El servicio se reconecta solo; los mensajes esperan en la cola durable |

**Base propia.** Los `eventId` procesados se guardan en un archivo H2 del servicio (`./datos/` al correrlo
desde acá; un volumen en Compose). No comparte base con el backend.

### Cómo levantarlo

Necesita RabbitMQ y Mailpit. Lo más simple es levantar los dos con Compose, desde la raíz:

```powershell
docker compose up -d rabbitmq mailpit
cd servicios
$env:RABBITMQ_USER="reclamos"; $env:RABBITMQ_PASSWORD="reclamos_dev"
.\mvnw.cmd -pl servicio-notificaciones spring-boot:run
```

Para que el backend publique, se arranca con `RECLAMOS_MENSAJERIA_HABILITADA=true` y las mismas variables de
RabbitMQ. Los correos se ven en `http://localhost:8025` y las colas en la consola de RabbitMQ,
`http://localhost:15672`, con el usuario y la contraseña de `.env`.

| Variable | Valor por defecto | Para qué |
| --- | --- | --- |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | `localhost`, `5672` | Dónde está RabbitMQ |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `guest` | Usuario de RabbitMQ; en Compose, el de `.env` |
| `SMTP_HOST`, `SMTP_PORT` | `localhost`, `1025` | SMTP de Mailpit |
| `NOTIFICACIONES_DB_URL` | `jdbc:h2:file:./datos/notificaciones` | Base de los `eventId` procesados |

| Test | Qué comprueba |
| --- | --- |
| `LectorMensajesTest` | El contrato: campos obligatorios, versión, UUID, email; los campos nuevos se ignoran y la fecha conserva su zona |
| `ProcesadorAvisosTest` | Correo y registro, repetido descartado, SMTP caído sin registrar y mensaje inválido sin enviar |
| `CorreoSmtpTest` | Destinatario, asunto y cuerpo del correo |
| `ConfiguracionReintentosTest` | Tres intentos ante una falla pasajera; ninguno ante un mensaje inválido, aunque llegue envuelto |
| `TopologiaRabbitTest` | Exchanges, colas, cola de errores y claves iguales al contrato |
| `FlujoCorreoIntegrationTest` | Con RabbitMQ y Mailpit de verdad (corre en la integración continua): el aviso llega como correo, el repetido no se manda y el inválido termina en la cola de errores |
