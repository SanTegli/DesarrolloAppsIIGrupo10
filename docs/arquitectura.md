# Arquitectura

El sistema es una aplicación en capas: la interfaz llama a la API REST, la API a los servicios y los
servicios al dominio. El dominio no depende de ningún framework: define interfaces (Repository,
estrategias, integraciones) y las capas de afuera las implementan.

## Capas

```mermaid
flowchart TB
  subgraph UI["Interfaz - frontend/ (React + Vite)"]
    COMP["Componentes: ingreso, listado, alta, detalle, administración"]
    API["src/api: cliente HTTP, sesión y token CSRF"]
  end
  subgraph SEG["Seguridad - reclamos-app/aplicacion/seguridad"]
    FIL["Cadena de filtros de Spring Security:<br/>CSRF, sesión, autorización, logout"]
  end
  subgraph REST["Presentación REST - reclamos-app/aplicacion/rest"]
    CTRL["ReclamoController, NotificacionController, UsuarioController,<br/>CatalogoController, AutenticacionController"]
    DTO["DTOs, RestMapper, ManejadorGlobalErrores"]
  end
  subgraph APP["Servicios - reclamos-app/aplicacion"]
    FAC["GestionReclamosFacade"]
    SRV["ReclamoService, ServicioAsignacion, ServicioNotificaciones,<br/>ServicioVencimientos y servicios de consulta"]
    PERM["PermisosReclamo"]
    EST["Estrategias de prioridad y asignación"]
    OBS["PublicadorEventosSpring, NotificacionesReclamoListener<br/>y AuditoriaListener"]
    TAR["TareaVencimientos"]
  end
  subgraph DOM["Dominio - reclamos-dominio (Java puro)"]
    MOD["Modelo y PoliticaTransiciones"]
    FAB["ReclamoFactory"]
    CON["Contratos: Repository, Estrategia, PublicadorEventos,<br/>CatalogoCobertura, Geocodificador, eventos"]
  end
  subgraph PERS["Persistencia - reclamos-persistencia"]
    ADP["Adaptadores RepositoryJpa"]
    SDJ["Interfaces Spring Data y orm.xml"]
  end
  DB[("MySQL en dev, H2 en tests")]
  UTIL["reclamos-comun: Validador y excepciones"]

  COMP --> API
  API -->|"HTTP + JSON, cookie de sesión"| FIL
  FIL --> CTRL
  CTRL --> FAC
  CTRL --> DTO
  FAC --> SRV
  SRV --> PERM
  SRV --> EST
  SRV --> FAB
  SRV --> MOD
  SRV --> CON
  SRV --> OBS
  TAR --> SRV
  OBS --> CON
  EST --> CON
  ADP -.->|implementa| CON
  ADP --> SDJ
  SDJ --> DB
  DOM --> UTIL
```

El recorrido de una operación es siempre el mismo:

```text
React → filtros de seguridad → Controller → Facade → Servicios → Dominio → Repository → JPA → MySQL
```

| Capa | Dónde está | Responsabilidad | No hace |
| --- | --- | --- | --- |
| Interfaz | `frontend/` | Mostrar reclamos y enviar acciones del usuario | No decide permisos: muestra las `accionesDisponibles` que calcula el backend |
| Seguridad | `reclamos-app/.../aplicacion/seguridad` | Login, sesión, CSRF y respuestas 401 y 403 con el error común | No decide qué puede hacer cada rol: eso sigue en el dominio y en `PermisosReclamo` |
| Presentación REST | `reclamos-app/.../aplicacion/rest` | Traducir HTTP y JSON a llamadas de la fachada; uniformar errores | No tiene lógica de negocio |
| Servicios | `reclamos-app/.../aplicacion` | Coordinar cada caso de uso dentro de una transacción | No conoce HTTP ni JPA |
| Dominio | `reclamos-dominio` | Reglas del negocio, creación del reclamo, contratos | No depende de Spring ni de JPA |
| Persistencia | `reclamos-persistencia` | Implementar los Repository con Spring Data JPA | No tiene reglas de negocio |
| Utilidades | `reclamos-comun` | Validación y excepciones compartidas | No depende de nada |

El detalle de las clases del dominio y de las tablas está en [modelo-dominio.md](modelo-dominio.md).

## Componentes

Cada módulo Maven es un componente con artefacto propio. Las dependencias van en un solo sentido.

```mermaid
flowchart LR
  FE["frontend<br/>React + Vite"]
  APP["reclamos-app<br/>servicios, fachada, eventos, REST"]
  PERS["reclamos-persistencia<br/>adaptadores JPA"]
  DOM["reclamos-dominio<br/>modelo, reglas, contratos"]
  COM["reclamos-comun<br/>validación y excepciones"]
  MYSQL[("MySQL 8.4")]
  COB["servicio-cobertura<br/>SOAP, proceso aparte"]
  PRI["servicio-prioridad<br/>REST 2, proceso aparte"]
  LLM["Gemini u OpenAI<br/>API externa"]
  GEO["Georef<br/>API externa"]
  MQ[("RabbitMQ<br/>reclamos.eventos")]
  NOT["servicio-notificaciones<br/>consumidor, proceso aparte"]
  MAIL["Mailpit<br/>SMTP de prueba"]

  FE -->|"REST /api"| APP
  APP --> PERS
  APP --> DOM
  PERS --> DOM
  DOM --> COM
  PERS --> MYSQL
  APP -.->|"SOAP ConsultarCobertura<br/>si origen es soap"| COB
  APP -.->|"REST POST /api/prioridades/evaluar<br/>si la estrategia es ia"| PRI
  PRI -->|"HTTPS"| LLM
  APP -->|"HTTPS GET /direcciones"| GEO
  APP -.->|"AMQP, outbox<br/>si la mensajería está habilitada"| MQ
  MQ -->|"cola notificaciones.email"| NOT
  NOT -->|"SMTP"| MAIL
```

`servicio-cobertura`, `servicio-prioridad` y `servicio-notificaciones` no son módulos del backend: son otras
aplicaciones, en `servicios/`, que se compilan, se prueban y se despliegan por separado. Lo único que
comparten con el backend son sus contratos: [`cobertura.xsd`](contratos/soap/cobertura.xsd),
[el OpenAPI de REST 2](contratos/README.md#rest-2-post-apiprioridadesevaluar) y
[el contrato de mensajes](contratos/mensajes.md).

## Componentes e interfaces

El diagrama anterior muestra quién depende de quién. Este muestra **por qué interfaz** se comunican, en la
notación de UML: cada círculo es una interfaz; la línea continua sale del componente que la **provee** y la
flecha punteada, del que la **requiere**. Ningún componente conoce la implementación de otro: solo su interfaz.

```mermaid
flowchart LR
  FE["«component»<br/>frontend"]
  APP["«component»<br/>reclamos-app"]
  DOM["«component»<br/>reclamos-dominio"]
  PERS["«component»<br/>reclamos-persistencia"]
  COM["«component»<br/>reclamos-comun"]
  COB["«component»<br/>servicio-cobertura"]
  PRI["«component»<br/>servicio-prioridad"]
  NOT["«component»<br/>servicio-notificaciones"]
  MQ["«broker»<br/>RabbitMQ"]
  GEO["«externo»<br/>Georef"]
  GEM["«externo»<br/>Gemini"]
  MY["«externo»<br/>MySQL"]
  MAIL["«externo»<br/>Mailpit"]

  I1(("API de<br/>reclamos"))
  I2(("Modelo e<br/>interfaces"))
  I3(("Repository"))
  I4(("Validación"))
  I5(("Consultar<br/>Cobertura"))
  I6(("Evaluar<br/>prioridad"))
  I7(("direcciones"))
  I8(("reclamos.<br/>eventos"))
  I9(("generate<br/>Content"))
  I10(("JDBC"))
  I11(("notificaciones.<br/>email"))
  I12(("SMTP"))

  FE -.-> I1 --- APP
  APP -.-> I2 --- DOM
  APP -.-> I3 --- PERS
  PERS -.-> I2
  DOM -.-> I4 --- COM
  PERS -.-> I10 --- MY
  APP -.->|"SOAP"| I5 --- COB
  APP -.->|"REST 2"| I6 --- PRI
  PRI -.->|"HTTPS"| I9 --- GEM
  APP -.->|"HTTPS"| I7 --- GEO
  APP -.->|"AMQP, publica"| I8 --- MQ
  NOT -.->|"AMQP, consume"| I11 --- MQ
  NOT -.-> I12 --- MAIL
```

| Componente | Provee | Requiere | Contrato |
| --- | --- | --- | --- |
| `frontend` | Interfaz web | API de reclamos | [`api-contract.md`](api-contract.md) y `openapi/reclamos.yaml` |
| `reclamos-app` | API de reclamos (REST 1) | Repository, `ConsultarCobertura`, evaluar prioridad, Georef, exchange `reclamos.eventos` | OpenAPI de REST 1 |
| `reclamos-dominio` | Modelo e interfaces: Repository, `EstrategiaPrioridad`, `EstrategiaAsignacion`, `CatalogoCobertura`, `Geocodificador`, `PublicadorEventos` | Validación de `reclamos-comun` | Las interfaces Java del paquete `dominio` |
| `reclamos-persistencia` | Implementación de los Repository | JDBC (MySQL) | `orm.xml` |
| `reclamos-comun` | Validación y excepciones | — | — |
| `servicio-cobertura` | `ConsultarCobertura` (SOAP) | Su catálogo, en un archivo propio | [`cobertura.xsd`](contratos/soap/cobertura.xsd) y el WSDL |
| `servicio-prioridad` | `POST /api/prioridades/evaluar` (REST 2) | API de Gemini (u OpenAI) | `openapi/prioridad.yaml` |
| `servicio-notificaciones` | Nada: solo consume | Cola `notificaciones.email`, SMTP | [`mensajes.md`](contratos/mensajes.md) |

Dentro del backend pasa lo mismo, con interfaces Java. Es la **transparencia de ubicación** de la Clase 1: el
servicio que usa la interfaz no sabe si la respuesta sale de la base, de otro proceso o de una API externa.

| Interfaz | La usa | Implementaciones | Se elige con |
| --- | --- | --- | --- |
| `CatalogoCobertura` | `ServicioAsignacion` | `CoberturaLocal` (base) o `CoberturaSoap` (servicio SOAP) | `reclamos.cobertura.origen` |
| `EstrategiaPrioridad` | `ReclamoService` | `PrioridadPorCategoria`, `PrioridadPorPalabrasClave` o `PrioridadConRespaldo` sobre `PrioridadPorIA` (REST 2) | `reclamos.prioridad.estrategia` |
| `Geocodificador` | `ServicioUbicaciones` | `GeocodificadorConCache` sobre `GeorefGeocodificador` | — |
| `SalidaDeAvisos` | `ServicioNotificaciones` | Ninguna salida, u `OutboxAvisos` (RabbitMQ) | `reclamos.mensajeria.habilitada` |
| `BrokerMensajes` | `PublicadorMensajes` | `BrokerRabbit` | — |

## Servicios orientados a componentes

Cada servicio tiene una responsabilidad y se puede probar por separado.

| Servicio | Responsabilidad |
| --- | --- |
| `ReclamoService` | Casos de uso de escritura: crear, asignar, reasignar y cambiar de estado |
| `ServicioAsignacion` | Decidir qué área atiende un reclamo: automática con la estrategia activa, o manual |
| `ServicioNotificaciones` | Decidir a quién se avisa en cada evento y guardar los avisos |
| `ServicioVencimientos` | Marcar los reclamos que pasaron su fecha límite y publicar `ReclamoVencido` |
| `ConsultaReclamosService` | Listar según el rol, buscar por número, avisos y acciones disponibles |
| `ConsultaCatalogosService` | Usuarios, categorías, barrios y áreas |

`PermisosReclamo` no es un servicio de negocio: es el componente que los servicios consultan para saber
quién puede crear y quién puede ver cada reclamo.

Los servicios se integran entre sí de dos formas:

- **Por interfaces.** Dependen de los contratos del dominio (`ReclamoRepository`,
  `EstrategiaPrioridad`, `EstrategiaAsignacion`, `PublicadorEventos`, `CatalogoCobertura`) y Spring
  inyecta la implementación.
- **Por eventos de dominio.** `ReclamoService` y `ServicioVencimientos` publican `ReclamoCreado`,
  `ReclamoAsignado`, `EstadoReclamoCambiado`, `ReclamoResuelto` y `ReclamoVencido`. Dos listeners
  reaccionan sin que quien publica los conozca: `NotificacionesReclamoListener` genera los avisos
  y `AuditoriaListener` registra cada evento.

`TareaVencimientos` ejecuta `ServicioVencimientos` cada minuto. El intervalo se cambia con
`reclamos.vencimientos.intervalo-ms` y la tarea se apaga con `reclamos.vencimientos.habilitado: false`.
Toda la configuración `reclamos.*` está tipada en `PropiedadesReclamos`: una clave mal escrita o un
intervalo que no sea positivo impiden el arranque, en lugar de quedar ignorados.

## Decisiones de diseño

| Decisión | Motivo |
| --- | --- |
| Mapeo JPA en `META-INF/orm.xml` en lugar de anotaciones | El dominio queda sin ninguna dependencia de JPA y se prueba con JUnit solo |
| Interfaces Repository en el dominio, adaptadores en persistencia | Los servicios no dependen de `JpaRepository`; en los tests se reemplazan por dobles en memoria |
| Transiciones en una tabla (`PoliticaTransiciones`) y no con el patrón State | Nueve transiciones se leen mejor en una tabla que repartidas en siete clases |
| Login con sesión del servidor, no JWT (PR 14) | La interfaz y el backend están en el mismo origen. La cookie `HttpOnly` no la puede leer JavaScript y cerrar sesión la invalida enseguida, cosa que un JWT no permite sin una lista de revocados |
| La sesión guarda solo el id del usuario | El rol sale del usuario guardado en la base en cada operación: un cambio de rol o una baja valen sin volver a entrar |
| Los avisos se generan dentro de la transacción | Si el listener de avisos falla se revierte todo el caso de uso; no quedan reclamos sin aviso |
| La auditoría corre después del commit | Solo registra hechos confirmados; una operación revertida no deja rastro falso |
| El frontend llama a `/api` en su mismo origen y un proxy reenvía al backend | No hace falta configurar CORS en Spring; funciona igual con Vite y con nginx |
| `accionesDisponibles` en la respuesta del reclamo | La interfaz no repite las reglas de transición: muestra un botón por cada acción |
| `open-in-view: false` y DTOs en REST | No se serializan entidades JPA ni se abren consultas durante la respuesta HTTP |
| Bloqueo optimista con `@Version` en `Reclamo` | Dos operaciones simultáneas sobre el mismo reclamo no se pisan: la segunda recibe `409 CONFLICTO_CONCURRENTE` |
| OpenAPI generado del código con springdoc | El contrato de Swagger no se desactualiza: `OpenApiTest` lo regenera en `docs/openapi/reclamos.yaml` |

## Login y sesión

Desde el PR 14 el usuario entra con email y contraseña. Antes se identificaba con el encabezado `X-Usuario-Id`,
y cualquiera podía hacerse pasar por cualquiera.

```mermaid
sequenceDiagram
  actor U as Usuario
  participant UI as React
  participant SEC as Filtros de Spring Security
  participant AUTH as AutenticacionController
  participant AM as AuthenticationManager
  participant DB as Usuarios en la base

  UI->>SEC: GET /api/auth/yo
  SEC-->>UI: 401 NO_AUTENTICADO y cookie XSRF-TOKEN
  U->>UI: email y contraseña
  UI->>SEC: POST /api/auth/login con X-XSRF-TOKEN
  SEC->>SEC: el token del encabezado coincide con la cookie
  SEC->>AUTH: pedido
  AUTH->>AM: autenticar(email, contraseña)
  AM->>DB: CargaDeUsuarios.loadUserByUsername(email)
  DB-->>AM: id, hash BCrypt, activo
  AM->>AM: BCrypt compara la contraseña
  AM-->>AUTH: autenticado
  AUTH->>AUTH: cambia el id de la sesión y guarda el usuario en ella
  AUTH-->>UI: 200 con el usuario y cookie JSESSIONID (HttpOnly)
  UI->>SEC: GET /api/reclamos con la cookie
  SEC->>SEC: sesión válida
  SEC-->>UI: los reclamos que ve ese usuario
```

| Pieza | Clase | Qué hace |
| --- | --- | --- |
| Configuración | `ConfiguracionSeguridad` | Rutas abiertas y protegidas, CSRF, logout, BCrypt y el `AuthenticationManager` |
| Carga del usuario | `CargaDeUsuarios` | Busca por email, sin distinguir mayúsculas. Sin contraseña o desactivado, no entra |
| Usuario de la sesión | `UsuarioAutenticado` | Solo el id y el email. Spring borra el hash después del login |
| CSRF | `CsrfParaAplicacionDeUnaPagina` | Token en la cookie `XSRF-TOKEN`, devuelto en `X-XSRF-TOKEN`. La configuración que recomienda Spring para aplicaciones de una sola página |
| Errores | `RespuestasDeSeguridad` | `401 NO_AUTENTICADO`, `403 CSRF_INVALIDO` y `403 ACCESO_DENEGADO`, con el mismo cuerpo que el resto |
| Registro | `ServicioCuentas` | Solo ciudadanos. Email en minúsculas y único, DNI único, contraseña de 8 a 72 bytes |

Los controladores reciben al usuario con `@AuthenticationPrincipal` y pasan su id a la fachada, que no cambió.
Las reglas de qué puede hacer cada rol siguen en el dominio y en `PermisosReclamo`: la seguridad solo
responde quién es.

## Integraciones de la Segunda Parte

El PR 8 dejó listos los puntos de entrada y cada integración llega en su PR. Los contratos están en
[`contratos/`](contratos/README.md).

| Integración | Dónde entra | Estado | Qué se agrega |
| --- | --- | --- | --- |
| Prioridad con IA (REST 2) | `EstrategiaPrioridad`, que devuelve `PrioridadCalculada` | **Hecho (PR 10)**: `PrioridadPorIA`, `PrioridadConRespaldo` y `servicio-prioridad`; ver abajo | — |
| Cobertura por SOAP | `CatalogoCobertura`, que usa `ServicioAsignacion` | **Hecho (PR 9)**: `CoberturaSoap` y `servicio-cobertura`; ver abajo | — |
| Geocodificación (API externa) | `Geocodificador` | **Hecho (PR 11)**: `GeorefGeocodificador`, `GeocodificadorConCache` y `ServicioUbicaciones`; ver abajo | — |
| Mensajería | `ServicioNotificaciones` | **Hecho (PR 12)**: outbox, `PublicadorMensajes`, RabbitMQ y `servicio-notificaciones`; ver abajo | — |
| Teléfono verificado (segunda API externa) | `CanalVerificacion` | **Hecho (PR 15)**: `CanalTelegram`, `BotTelegram` y `ServicioVerificacionTelefono`; ver abajo | — |
| Foto del reclamo | `AlmacenFotos` | **Hecho (PR 16)**: `ServicioFotos`, `ProcesadorFotos` y `AlmacenFotosLocal` (volumen); ver abajo | — |
| Duplicados y apoyos | `ServicioDuplicados`, `ServicioApoyos` | **Hecho (PR 17)**: candidatos cercanos con Haversine y apoyos únicos; ver abajo | — |
| Tableros | `ConsultasTablero` (lado de lectura) | **Hecho (PR 18)**: `ConsultasTableroJpa` y `ServicioTablero`; ver abajo | — |

`EstrategiaAsignacion` no cambia: sigue eligiendo entre las candidatas, que ahora entrega
`CatalogoCobertura` en lugar del Repository.

### Cobertura por SOAP

Con `reclamos.cobertura.origen: soap`, `ServicioAsignacion` recibe `CoberturaSoap` en lugar de
`CoberturaLocal`. No cambia nada más: la interfaz es la misma, y `ServicioAsignacion` no sabe si las
candidatas salen de la base o de otro proceso.

```mermaid
sequenceDiagram
  autonumber
  participant RS as ReclamoService
  participant SA as ServicioAsignacion
  participant CS as CoberturaSoap<br/>(Adapter)
  participant WS as servicio-cobertura<br/>(SOAP)
  participant AR as AreaMunicipalRepository
  participant EA as EstrategiaAsignacion

  RS->>SA: asignarAutomaticamente(reclamo)
  SA->>CS: buscarCandidatas(municipio, barrio, categoría)
  CS->>WS: ConsultarCoberturaRequest (HTTP POST /ws, hasta 2 s)
  alt responde con áreas
    WS-->>CS: ConsultarCoberturaResponse con ids 2 y 4
    CS->>AR: buscarPorId(2), buscarPorId(4)
    AR-->>CS: áreas del backend
    CS-->>SA: candidatas activas del municipio, por id
    SA->>EA: seleccionarArea(reclamo, candidatas)
    EA-->>SA: área elegida
    SA-->>RS: reclamo ASIGNADO
  else responde sin áreas
    WS-->>CS: ConsultarCoberturaResponse vacía
    CS-->>SA: lista vacía
    SA-->>RS: reclamo INGRESADO
  else SOAP Fault o no contesta a tiempo
    CS-->>SA: ServicioExternoNoDisponibleException
    SA-->>RS: INGRESADO con asignacionPendiente
  end
```

| Pieza | Responsabilidad |
| --- | --- |
| `servicio-cobertura` | Dueño de la cobertura: qué área atiende qué categoría en qué barrio, en `cobertura.json`. Publica el WSDL generado de `cobertura.xsd` y valida cada pedido contra el XSD |
| `CoberturaSoap` | Adapter entre `CatalogoCobertura` y la operación SOAP. Traduce los mensajes, aplica el tiempo límite y convierte cualquier falla en `ServicioExternoNoDisponibleException` |
| `ServicioAsignacion` | Igual que antes: si la cobertura no está disponible, marca la asignación pendiente; si no, la estrategia elige |
| Backend | Sigue siendo dueño de los datos de cada área (nombre, email, activa). Descarta las áreas que no tiene, las inactivas y las de otro municipio |

El administrador ve los reclamos con `asignacionPendiente` en la pantalla de administración y los asigna
a mano, como cualquier reclamo sin área.

### Prioridad con IA

Con `reclamos.prioridad.estrategia: ia`, la estrategia de prioridad es `PrioridadConRespaldo`, que envuelve
a `PrioridadPorIA`. `ReclamoService` no cambia: sigue pidiendo una `PrioridadCalculada` a la estrategia.

```mermaid
sequenceDiagram
  autonumber
  participant RS as ReclamoService
  participant PR as PrioridadConRespaldo<br/>(Decorator)
  participant PI as PrioridadPorIA
  participant CL as ClienteServicioPrioridad<br/>(circuit breaker)
  participant SP as servicio-prioridad<br/>(REST 2)
  participant LLM as Gemini

  RS->>PR: calcular(reclamo)
  PR->>PI: calcular(reclamo)
  PI->>CL: sugerir(categoría, descripción)
  alt circuito cerrado
    CL->>SP: POST /api/prioridades/evaluar (hasta 6 s)
    SP->>LLM: generateContent con el esquema de respuesta
    LLM-->>SP: {"prioridad": "CRITICA", "motivo": "..."}
    SP-->>CL: 200 con prioridad, motivo, proveedor y modelo
    CL-->>PI: sugerencia
    PI-->>PR: la mayor entre categoría y sugerencia
    PR-->>RS: PrioridadCalculada
  else REST 2 falla, tarda o el circuito está abierto
    CL-->>PI: ServicioExternoNoDisponibleException
    PI-->>PR: la excepción sigue
    PR-->>RS: prioridad de la categoría, origen RESPALDO
  end
```

| Pieza | Responsabilidad |
| --- | --- |
| `servicio-prioridad` | El componente de IA, publicado como servicio. Arma la consulta con los criterios de `instrucciones-prioridad.txt`, la manda al proveedor configurado y valida la respuesta. Recibe solo categoría y descripción |
| `ProveedorIA` y sus adaptadores | `GeminiAdapter`, `OpenAiAdapter` y `SimuladoAdapter`: traducen la consulta a la API de cada proveedor. Se elige con `prioridad.ia.proveedor` |
| `ClienteServicioPrioridad` | Llama a REST 2 con 1 s para conectar y 6 s para la respuesta. Si la mitad de las últimas 5 llamadas falló, abre el circuito y durante 30 s falla sin llamar |
| `PrioridadPorIA` | La IA puede subir la prioridad de la categoría, pero no bajarla |
| `PrioridadConRespaldo` | Si la IA no responde, usa la prioridad de la categoría y marca el origen `RESPALDO` |

La IA se llama dentro de la transacción del alta: en el peor caso, el alta tarda unos 6 segundos más. Con
el circuito abierto, el respaldo es inmediato.

### Ubicación con Georef (API externa)

La dirección se busca antes del alta, fuera de cualquier transacción: el ciudadano escribe la dirección,
toca "Buscar en el mapa" y elige una de las sugerencias. El alta manda la elegida y el backend la valida.

```mermaid
sequenceDiagram
  autonumber
  actor C as Ciudadano
  participant UI as React
  participant UC as UbicacionController
  participant SU as ServicioUbicaciones
  participant CA as GeocodificadorConCache<br/>(Proxy)
  participant GG as GeorefGeocodificador<br/>(Adapter)
  participant GR as Georef

  C->>UI: escribe "Belgrano 450" y toca Buscar en el mapa
  UI->>UC: GET /api/ubicaciones/sugerencias?direccion=…&barrioId=2
  UC->>SU: sugerir(direccion, barrio)
  SU->>CA: sugerir(direccion, barrio)
  alt no está en la caché
    CA->>GG: sugerir(direccion, barrio)
    GG->>GR: GET /direcciones?direccion=…&departamento=Quilmes&provincia=Buenos Aires
    GR-->>GG: direcciones con calle, localidad y ubicación
    GG-->>CA: SugerenciaUbicacion (nomenclatura y coordenadas)
  end
  CA-->>SU: sugerencias
  SU-->>UI: las que tienen coordenadas dentro de Quilmes, hasta 5
  C->>UI: elige "GRL BELGRANO 450, Bernal Oeste" y crea el reclamo
  UI->>SU: POST /api/reclamos con ubicacion (por ReclamoController y la fachada)
  Note over SU: confirmar: dentro del recuadro → Ubicacion CONFIRMADA, proveedor georef
```

| Pieza | Responsabilidad |
| --- | --- |
| `GeorefGeocodificador` | Adapter y capa anticorrupción: arma la consulta de Georef y traduce su respuesta a `SugerenciaUbicacion`. Un 400 de Georef es "no encontrada"; un error, un tiempo agotado (1 s para conectar, 3 s para responder) o una respuesta con otra forma es `ServicioExternoNoDisponibleException` |
| `GeocodificadorConCache` | Proxy de caché: misma interfaz; la misma dirección en el mismo municipio se responde de memoria durante 10 minutos. Las fallas no se guardan |
| `ServicioUbicaciones` | Filtra las sugerencias sin coordenadas o fuera del recuadro del municipio y, en el alta, valida la elegida. Si Georef no responde, devuelve `disponible: false` en lugar de fallar |
| `ReclamoService` | Guarda la `Ubicacion` que le pasa la fachada: confirmada o pendiente |

El recuadro de cada municipio está en `reclamos.geocodificacion.recuadros`. Es un filtro grueso: evita
coordenadas de otra provincia o inventadas en el navegador, pero no prueba que el punto esté dentro del
límite exacto del partido.

### Mensajería: outbox, RabbitMQ y servicio de notificaciones

Cada aviso que genera `ServicioNotificaciones` sigue guardándose como aviso interno y, con
`reclamos.mensajeria.habilitada: true`, también sale por correo. La publicación es asincrónica: el alta no
espera a RabbitMQ ni al SMTP.

```mermaid
sequenceDiagram
  autonumber
  participant SN as ServicioNotificaciones
  participant OB as OutboxAvisos
  participant DB as MySQL<br/>mensajes_salientes
  participant PU as PublicadorMensajes<br/>(cada 2 s)
  participant MQ as RabbitMQ<br/>reclamos.eventos
  participant LI as servicio-notificaciones
  participant MP as Mailpit

  Note over SN,DB: dentro de la transacción del alta
  SN->>DB: guarda el aviso interno
  SN->>OB: enviar(aviso, RECLAMO_CREADO)
  OB->>DB: fila PENDIENTE con eventId y JSON
  Note over SN,DB: commit: aviso y mensaje, o ninguno
  PU->>DB: hasta 50 pendientes, los más viejos primero
  PU->>MQ: publica con clave aviso.reclamo-creado
  MQ-->>PU: confirmación (publisher confirm)
  PU->>DB: PUBLICADO
  MQ->>LI: cola notificaciones.email
  alt eventId nuevo
    LI->>MP: correo por SMTP
    LI->>LI: registra el eventId
  else eventId repetido
    LI->>LI: lo descarta
  end
  LI-->>MQ: ACK
```

| Pieza | Responsabilidad |
| --- | --- |
| `OutboxAvisos` | Guarda el mensaje en `mensajes_salientes`, en la transacción del caso de uso. Si el alta se revierte, el mensaje tampoco existe |
| `PublicadorMensajes` | Publica los pendientes con confirmación del broker y los marca publicados. Si RabbitMQ no responde, quedan pendientes con el error y salen en la próxima pasada |
| `BrokerRabbit` | Adapter sobre `RabbitTemplate`: mensaje persistente, JSON, con el `eventId` como `message-id` |
| `servicio-notificaciones` | Consumidor idempotente: descarta los `eventId` ya procesados, manda el correo y registra. Reintenta 3 veces; después, a la cola de errores |

Fallas: con RabbitMQ caído, el reclamo se crea igual y los mensajes esperan en la outbox; con el SMTP caído,
el mensaje espera en la cola y, si se agotan los reintentos, queda en `notificaciones.email.dlq` para
revisarlo. No hay entrega exactamente una vez: si el consumidor cae entre el envío y el registro, el correo se
repite. Está documentado en [el contrato](contratos/mensajes.md).

### Teléfono verificado con Telegram (segunda API externa)

Para crear o reabrir reclamos, un ciudadano tiene que verificar su teléfono, y **un número sirve para una
sola cuenta**: así no se abren cuentas en serie para cargar reclamos falsos. Telegram es la segunda API
externa del sistema (la primera es Georef): su Bot API es gratuita y, al compartir el contacto, Telegram
manda el número real de la cuenta, que el usuario no puede escribir a mano.

```mermaid
sequenceDiagram
  autonumber
  actor C as Ciudadano
  participant UI as React
  participant VC as VerificacionTelefonoController
  participant SV as ServicioVerificacionTelefono
  participant BT as BotTelegram<br/>(tarea cada 2 s)
  participant CT as ClienteTelegram<br/>(Adapter)
  participant TG as Telegram Bot API

  C->>UI: Crear reclamo (sin teléfono verificado)
  UI->>VC: POST /api/verificacion-telefono
  VC->>SV: iniciar(usuario)
  SV-->>UI: código K7M2Q9XP y enlace t.me/bot?start=K7M2Q9XP
  C->>TG: abre el enlace, toca Iniciar
  BT->>CT: recibir(desde)
  CT->>TG: getUpdates?offset=…
  TG-->>CT: /start K7M2Q9XP
  BT->>CT: enviar("Compartí tu teléfono", botón de contacto)
  C->>TG: comparte su contacto
  BT->>CT: recibir(desde)
  CT-->>BT: contacto propio con phone_number
  BT->>SV: confirmar(código, teléfono)
  Note over SV: normaliza a E.164, controla vigencia,<br/>bloqueo y que el número no esté en otra cuenta
  BT->>CT: enviar("Listo, tu teléfono quedó verificado")
  UI->>VC: GET /api/auth/yo cada 3 s
  VC-->>UI: telefonoVerificado: +54 •••••••0000 → muestra el formulario
```

| Pieza | Responsabilidad |
| --- | --- |
| `ServicioVerificacionTelefono` | Emite códigos de un solo uso (8 caracteres, `SecureRandom`, vencen a los 10 minutos, hasta 3 por hora) y los confirma. Devuelve un `ResultadoVerificacion` en lugar de lanzar excepciones, para que el bot conteste algo claro |
| `CanalVerificacion` | Interfaz del canal. `CanalTelegram` arma el enlace al bot; `CanalSimulado` no tiene enlace y habilita `POST /api/verificacion-telefono/simulada` |
| `BotTelegram` | Tarea programada (solo con `reclamos.telegram.modo=telegram`) que consulta `getUpdates` cada 2 s, pide el contacto y confirma. Solo acepta el contacto del mismo usuario que lo manda. Si Telegram no responde, lo registra y vuelve a probar en el siguiente ciclo |
| `ClienteTelegram` | Adapter y capa anticorrupción: el JSON de Telegram no sale de esta clase. La URL lleva el token, así que nunca conserva la causa de una excepción ni loguea la URL |

Se usa *long polling* (`getUpdates`) y no un webhook porque el backend corre en una computadora sin
dirección pública. El token se lee de `TELEGRAM_BOT_TOKEN` (solo en `.env`) y `toString` de la configuración
lo oculta. En modo `simulado`, que es el predeterminado, no hay bot ni conexión con Telegram.

### Controles de uso y reaperturas

Antes de crear un reclamo, `ReclamoService` pasa al ciudadano por una cadena de controles
(`ControlesDeUso`): teléfono verificado → cuenta sin bloquear → límite diario (5 reclamos en 24 horas). Al
reabrir se aplican los dos primeros. Cada control corta con su propio error (`403` o `429`) o deja pasar al
siguiente. Los límites están en `reclamos.uso` y `reclamos.reaperturas`.

Las reglas de la reapertura viven en el dominio (`Reclamo` con `PoliticaReapertura`): motivo obligatorio,
una vez y dentro de 7 días desde la última resolución. La cantidad y la fecha se calculan del historial, así
que no hay columnas nuevas en `reclamos` y se cuentan también las reaperturas viejas. El `@Version` del
reclamo evita que un doble clic lo reabra dos veces. El administrador bloquea y desbloquea cuentas de
ciudadanos desde la interfaz; una cuenta bloqueada sigue pudiendo entrar y consultar.

### Foto del reclamo

Cada reclamo nuevo lleva una foto del problema: es la prueba que ve el agente antes de salir y la que
permite comparar duplicados (PR 17). El alta pasa a `multipart/form-data`, con la parte `reclamo` (el JSON
de siempre) y la parte `foto`.

```mermaid
sequenceDiagram
  autonumber
  actor C as Ciudadano
  participant UI as React
  participant RC as ReclamoController
  participant F as GestionReclamosFacade
  participant SF as ServicioFotos
  participant AL as AlmacenFotosLocal<br/>(Adapter)
  participant RS as ReclamoService
  participant DB as MySQL

  C->>UI: completa el alta y elige la foto
  UI->>RC: POST /api/reclamos (multipart: reclamo + foto)
  RC->>F: crear(…, FotoSubida)
  F->>SF: preparar(foto)
  Note over SF: ImageIO: ¿JPEG o PNG? ¿hasta 4000 × 4000?<br/>re-codifica a JPEG sin EXIF
  SF->>AL: guardarPendiente(jpeg)
  AL-->>F: referencia aleatoria (.pendientes/…)
  F->>RS: crear(…, referencia) en una transacción
  RS->>DB: INSERT reclamos (foto = referencia)
  alt el alta se confirmó
    F->>SF: confirmar(referencia)
    SF->>AL: mueve el archivo de .pendientes/
  else el alta falló (404, 403, 409…)
    F->>SF: descartar(referencia)
    SF->>AL: borra el archivo pendiente
  end
  RC-->>UI: 201 con foto: /api/reclamos/{numero}/foto
```

| Pieza | Responsabilidad |
| --- | --- |
| `ProcesadorFotos` | Reconoce el formato por el contenido (solo JPEG o PNG), lee las dimensiones del encabezado antes de decodificar (hasta 4000 × 4000) y re-codifica a JPEG con calidad 0,85. Re-codificar descarta los metadatos: el EXIF con el GPS no se guarda |
| `AlmacenFotos` | Interfaz del almacén (Adapter): guardar pendiente, confirmar, descartar y leer. `AlmacenFotosLocal` usa un directorio; Firebase Storage o S3 serían otra implementación |
| `AlmacenFotosLocal` | Nombres aleatorios (`<32 hex>.jpg`), pendientes en `.pendientes/` y movimiento atómico al confirmar. Rechaza cualquier referencia con otra forma, así no se puede leer fuera del directorio |
| `ServicioFotos` | Coordina los dos pasos. Si confirmar falla después del alta, lo registra y el reclamo queda sin foto visible, en lugar de responder un error por un reclamo que sí se creó |
| `GestionReclamosFacade` | Prepara la foto antes de la transacción del alta y la confirma o descarta según cómo terminó |

**El archivo y la base no comparten transacción.** Por eso la foto se guarda primero como pendiente y se
confirma cuando el reclamo ya está en la base; si el alta falla, se borra. Así no queda un archivo huérfano
por cada alta rechazada, ni un reclamo que apunte a un archivo a medio escribir.

**Acceso.** `GET /api/reclamos/{numero}/foto` usa los mismos permisos que el detalle. La foto no se sirve
como archivo estático ni con una URL pública: sin sesión, `401`.

**Límites de tamaño, en tres lugares:** nginx (6 MB, `client_max_body_size`), Spring
(`spring.servlet.multipart.max-file-size: 5MB`) y `ProcesadorFotos` (5 MB y 4000 px). Los dos primeros cortan
antes de leer el archivo entero; el tercero vale aunque cambie el transporte.

### Duplicados y apoyos

Antes de crear un reclamo, si el ciudadano marcó el punto en el mapa, la interfaz pregunta por reclamos
abiertos de la misma categoría a pocos metros. Si es el mismo problema, lo apoya; si no, sigue con el alta.
Depende de la geocodificación del PR 11: sin coordenadas no hay cercanía.

```mermaid
sequenceDiagram
  autonumber
  actor C as Ciudadano
  participant UI as React
  participant RC as ReclamoController
  participant SD as ServicioDuplicados
  participant RR as ReclamoRepository
  participant SA as ServicioApoyos

  C->>UI: completa el alta, con el punto elegido en el mapa, y toca Crear
  UI->>RC: GET /api/reclamos/candidatos-duplicados?categoriaId&latitud&longitud
  RC->>SD: candidatos(…)
  SD->>RR: abiertos de la categoría dentro del recuadro (± 50 m)
  RR-->>SD: reclamos
  Note over SD: Haversine exacto, ≤ 50 m, del más cercano, hasta 5
  SD-->>UI: tarjetas sin datos del autor
  alt "Es el mismo: apoyar"
    UI->>RC: POST /api/reclamos/{numero}/apoyo
    RC->>SA: apoyar (teléfono verificado, cuenta sin bloquear)
    SA-->>UI: cantidadApoyos
  else "Es otro problema: continuar"
    UI->>RC: POST /api/reclamos (multipart)
  end
```

| Pieza | Responsabilidad |
| --- | --- |
| `Distancia` (dominio) | Haversine y la conversión de metros a grados para el recuadro |
| `ReclamoRepository.buscarAbiertosConUbicacionEn` | Primer filtro barato en la base: categoría, estado abierto, ubicación confirmada y un recuadro alrededor del punto |
| `ServicioDuplicados` | Distancia exacta, radio (50 m, máximo 200) y cantidad (hasta 5). Marca los reclamos propios y los que ya apoya quien consulta |
| `Apoyo` (dominio) | Nadie apoya su propio reclamo ni uno cerrado. No cambia la prioridad ni da permisos |
| `ServicioApoyos` | Apoyar y retirar, idempotentes. Usa los mismos controles que reabrir: teléfono verificado y cuenta sin bloquear |
| Restricción única `(ciudadano_id, reclamo_id)` | Un apoyo por ciudadano y reclamo, aun con dos pedidos simultáneos. La fachada responde el estado actual si el segundo choca |

**Privacidad.** Las tarjetas no muestran quién reclamó. La foto de un reclamo que puede aparecer como
duplicado la puede ver cualquier ciudadano mientras siga abierto; por eso el alta pide evitar caras y
patentes. El detalle completo sigue siendo solo del autor, del área y de la administración.

**La búsqueda es una ayuda, no una regla.** Si falla, el alta sigue. La API no impide crear un reclamo
parecido: decide el vecino. La vinculación de dos reclamos ya creados quedó fuera del alcance.

### Tableros

Dos tableros, con tarjetas y una tabla:

- **Público:** sin sesión y sin datos de personas. Muestra los creados en el período y cuántos hay hoy pendientes,
  resueltos y cerrados, con filtro de barrio y categoría.
- **Gestión:** para el municipio. Muestra pendientes, vencidos por fecha, sin asignar y una tabla por área y
  barrio. Cada agente ve solo su área.

**Lado de lectura separado (Domain Reporting, CQRS: Clase 5).** Las métricas no pasan por el agregado ni por
`ReclamoRepository`. Recorrer reclamos con el Repository para contarlos es el antipatrón que marca la clase:
cargaría cada reclamo con su historial y sus relaciones. En su lugar hay un puerto propio, `ConsultasTablero`
(en el dominio, paquete `reporte`), que devuelve proyecciones planas. `ConsultasTableroJpa` lo implementa
con JPQL agregado (`count`, `sum(case …)`, `group by`) y expresiones `select new`, así la base cuenta y agrupa.

```mermaid
flowchart LR
  UI[React: pestaña Tablero] --> TC[TableroController]
  TC --> F[GestionReclamosFacade]
  F --> ST[ServicioTablero<br/>período, rol y porcentajes]
  ST --> CT[[ConsultasTablero<br/>puerto de lectura]]
  CT -.implementa.- CJ[ConsultasTableroJpa<br/>JPQL agregado]
  CJ --> DB[(MySQL)]
  ST -. usuarios, barrios, categorías .-> R[[Repositories]]
```

| Pieza | Responsabilidad |
| --- | --- |
| `ConsultasTablero` | Tres consultas: creados en un período, cuántos hay por estado y pendientes y vencidos por área y barrio |
| `ConsultasTableroJpa` | JPQL con proyecciones (`ConteoPorEstado`, `FilaPendientes`); los filtros opcionales se agregan solo si vienen |
| `ServicioTablero` | Período por defecto (30 días) y máximo (366), qué ve cada rol y los porcentajes. Si el denominador es cero, el porcentaje es `null`: "sin casos" |

No hace falta un modelo de lectura aparte ni tablas denormalizadas: con los volúmenes de un municipio, las
consultas agregadas sobre `reclamos` alcanzan. Si crecieran, el puerto permite cambiar la implementación por
una tabla proyectada desde los eventos de dominio sin tocar el servicio ni la interfaz.

## Límites conocidos

- **Correo de laboratorio.** Con la mensajería habilitada, los avisos salen por correo, pero a Mailpit, que
  los muestra en un buzón web y no los entrega a casillas reales. No hay SMS.
- **Un solo publicador.** La outbox la publica una tarea del backend; con dos instancias del backend, las dos
  publicarían los mismos pendientes (el consumidor descarta los repetidos, pero sobra trabajo).
- **Sin límite de intentos de login.** Un atacante puede probar contraseñas sin freno. El PR 15 limita
  reclamos y códigos de verificación, no los intentos de login.
- **Límites por cuenta, no por persona.** El teléfono verificado frena las cuentas en serie, pero alguien con
  varias líneas de Telegram puede verificar varias cuentas.
- **Un solo bot por backend.** Con dos instancias del backend en modo `telegram`, las dos leerían las mismas
  actualizaciones.
- **Sin confirmación del email.** El registro no verifica que el correo sea de quien se registra.
- **Una cuenta desactivada conserva su sesión abierta** hasta que vence. Cada operación se valida con el
  usuario de la base, así que igual no puede crear reclamos: el dominio la rechaza con `409`.
- **Fotos sin girar ni anonimizar.** La foto no se gira según la orientación que guardó el teléfono en el
  EXIF, así que una foto vertical puede verse acostada. Tampoco se ocultan caras ni patentes: se le pide al
  ciudadano que las evite. Difuminarlas automáticamente necesitaría un modelo de detección, fuera del alcance.
- **Fotos en un solo disco.** El almacén local no se replica: con dos instancias del backend haría falta un
  almacén compartido (otra implementación de `AlmacenFotos`). Un reclamo borrado de la base deja su foto.
- **Duplicados solo con el punto en el mapa.** Un reclamo sin la ubicación confirmada no se compara ni
  aparece como candidato. Tampoco se comparan textos ni fotos.
- **Apoyos sin aviso.** El autor ve cuántos vecinos apoyan su reclamo en el detalle, pero no recibe un aviso
  cuando alguien lo apoya.
- **Tableros sin historia.** El público dice cuántos reclamos hay hoy en cada estado, no cuántos había en una
  fecha pasada. Tampoco hay gráficos ni mapas: tarjetas y una tabla.
- **Listados sin paginar.** Los filtros se aplican en memoria sobre los reclamos visibles.
- **Datos maestros de solo lectura.** Áreas, categorías, barrios y usuarios salen de la semilla; no
  hay alta ni edición desde la interfaz.

## Decisiones de la Segunda Parte

| Tema | Decisión |
| --- | --- |
| Geolocalización | El ciudadano sigue eligiendo el barrio. Georef normaliza la dirección y devuelve sugerencias con coordenadas; el ciudadano elige una. No se deduce el barrio |
| Servicio SOAP | Recibe municipio, barrio y categoría y devuelve las áreas que los cubren. Es el dueño de la cobertura |
| IA | Estima la prioridad a partir del texto. Gemini por su nivel gratuito; OpenAI queda como segundo adaptador. La categoría la sigue eligiendo el ciudadano |
| IA sincrónica o por cola | Sincrónica, dentro del alta, con tiempo límite y respaldo por categoría |
| Identificador de mensaje | Cada aviso que sale por RabbitMQ lleva el `eventId` de su fila de outbox; los eventos de dominio no cambian |
| Más de un proceso | Cobertura, prioridad y notificaciones son aplicaciones separadas, en `servicios/` |
