# Arquitectura

El sistema es una aplicación en capas: la interfaz llama a la API REST, la API a los servicios y los
servicios al dominio. El dominio no depende de ningún framework: define interfaces (Repository,
estrategias, integraciones) y las capas de afuera las implementan.

## Capas

```mermaid
flowchart TB
  subgraph UI["Interfaz - frontend/ (React + Vite)"]
    COMP["Componentes: listado, alta, detalle, administración"]
    API["src/api: cliente HTTP y X-Usuario-Id"]
  end
  subgraph REST["Presentación REST - reclamos-app/aplicacion/rest"]
    CTRL["ReclamoController, NotificacionController,<br/>UsuarioController, CatalogoController"]
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
  API -->|"HTTP + JSON"| CTRL
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
React → Controller → Facade → Servicios → Dominio → Repository → JPA → MySQL
```

| Capa | Dónde está | Responsabilidad | No hace |
| --- | --- | --- | --- |
| Interfaz | `frontend/` | Mostrar reclamos y enviar acciones del usuario | No decide permisos: muestra las `accionesDisponibles` que calcula el backend |
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
```

`servicio-cobertura` y `servicio-prioridad` no son módulos del backend: son otras aplicaciones, en
`servicios/`, que se compilan, se prueban y se despliegan por separado. Lo único que comparten con el
backend son sus contratos: [`cobertura.xsd`](contratos/soap/cobertura.xsd) y
[el OpenAPI de REST 2](contratos/README.md#rest-2-post-apiprioridadesevaluar).

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
| Identidad con el encabezado `X-Usuario-Id` | El Hito 1 no pide autenticación; el rol sale del usuario guardado en la base |
| Los avisos se generan dentro de la transacción | Si el listener de avisos falla se revierte todo el caso de uso; no quedan reclamos sin aviso |
| La auditoría corre después del commit | Solo registra hechos confirmados; una operación revertida no deja rastro falso |
| El frontend llama a `/api` en su mismo origen y un proxy reenvía al backend | No hace falta configurar CORS en Spring; funciona igual con Vite y con nginx |
| `accionesDisponibles` en la respuesta del reclamo | La interfaz no repite las reglas de transición: muestra un botón por cada acción |
| `open-in-view: false` y DTOs en REST | No se serializan entidades JPA ni se abren consultas durante la respuesta HTTP |
| Bloqueo optimista con `@Version` en `Reclamo` | Dos operaciones simultáneas sobre el mismo reclamo no se pisan: la segunda recibe `409 CONFLICTO_CONCURRENTE` |
| OpenAPI generado del código con springdoc | El contrato de Swagger no se desactualiza: `OpenApiTest` lo regenera en `docs/openapi/reclamos.yaml` |

## Integraciones de la Segunda Parte

El PR 8 dejó listos los puntos de entrada y cada integración llega en su PR. Los contratos están en
[`contratos/`](contratos/README.md).

| Integración | Dónde entra | Estado | Qué se agrega |
| --- | --- | --- | --- |
| Prioridad con IA (REST 2) | `EstrategiaPrioridad`, que devuelve `PrioridadCalculada` | **Hecho (PR 10)**: `PrioridadPorIA`, `PrioridadConRespaldo` y `servicio-prioridad`; ver abajo | — |
| Cobertura por SOAP | `CatalogoCobertura`, que usa `ServicioAsignacion` | **Hecho (PR 9)**: `CoberturaSoap` y `servicio-cobertura`; ver abajo | — |
| Geocodificación (API externa) | `Geocodificador` | **Hecho (PR 11)**: `GeorefGeocodificador`, `GeocodificadorConCache` y `ServicioUbicaciones`; ver abajo | — |
| Mensajería | `ServicioNotificaciones` | Avisos internos | Outbox, RabbitMQ y `servicio-notificaciones` |

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

## Límites conocidos

- **Avisos simulados.** Las notificaciones se guardan en la base con canal `INTERNO`; no se envían
  correos ni SMS.
- **Sin autenticación.** El usuario se identifica con `X-Usuario-Id`; cualquiera puede elegir
  cualquier usuario.
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
