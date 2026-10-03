# Sistema de Reclamos Urbanos — Resumen PR1, PR2 y PR3

Documento técnico del estado inspeccionado el **03/10/2026**, incluyendo archivos sin commit. Para instalación y ejecución en Windows, consultar [README_INSTRUCTIVO.md](README_INSTRUCTIVO.md).

| Entrega | Evidencia Git | Estado verificado |
| --- | --- | --- |
| PR1 | `f93a30e`, integrado por merge `fdc8793` | Foundation, dominio y contratos integrados. |
| PR2 | `2c27785`, integrado por merge `af7cb8e` | Persistencia integrada; `main` y `origin/main` apuntan a `af7cb8e`. |
| PR3 | Archivos locales nuevos en `reclamos-app/.../aplicacion/` (main y test), más cambios existentes de README y comentario en `application.yml` | Implementación actual sin commit; no se verificó publicación remota. |

La rama activa es `main`. `feature/03-services-rest` existe localmente, pero apunta a `2c27785`, igual que `feature/02-persistence-repositories`. El contenido de PR3 documentado aquí pertenece al working tree, no a un commit identificado como PR3.

## 1. Objetivo del sistema

Un ciudadano crea un reclamo urbano indicando categoría, barrio, descripción y dirección. La categoría aporta un SLA en horas y prioridad base; una Strategy calcula la prioridad y otra elige automáticamente el área municipal compatible. Los agentes del área toman y resuelven reclamos; el ciudadano confirma el cierre o reabre, y el administrador asigna, reasigna o rechaza dentro de las reglas permitidas.

Cada operación conserva el historial de estados. Los eventos generan notificaciones internas persistidas como evidencia del Observer; los envíos son simulados, sin correo ni SMS reales.

## 2. Arquitectura actual

```text
REST (controllers, DTOs, RestMapper, errores)
  ↓
GestionReclamosFacade
  ↓
Servicios / casos de uso
  ↓
Dominio y reglas
  ↓
Repository contracts (puertos)
  ↓
Adaptadores JPA / Spring Data
  ↓
MySQL en dev / H2 en tests
```

El esquema representa responsabilidades: los servicios coordinan directamente objetos de dominio y puertos Repository. La persistencia implementa esos puertos; el dominio no llama ni depende de Spring Data.

| Módulo Maven | Responsabilidad y dependencias |
| --- | --- |
| `reclamos-comun` | `Validador` y excepciones; sin dependencias de framework en producción. |
| `reclamos-dominio` | Modelo, Factory, transiciones, eventos, Repository y Strategy; depende de `reclamos-comun`. |
| `reclamos-persistencia` | `META-INF/orm.xml`, adaptadores, interfaces Spring Data y SQL semilla; depende de dominio y Spring Data JPA. |
| `reclamos-app` | `ReclamosApplication`, configuración, servicios, Facade, Observer y REST; depende de persistencia, Web y Validation. |

El reactor está definido en `backend/pom.xml`, con Java 21 y Spring Boot 3.5.9. Las dependencias principales siguen `app → persistencia → dominio → comun`. El dominio permanece desacoplado de Spring y JPA: los mappings son XML externos, aunque el modelo contempla constructores protegidos e identidad compatibles con persistencia.

## 3. PR1 — Foundation + Domain + Shared Contracts

El diff del commit `f93a30e` confirma la reorganización a Maven multimódulo y la sustitución del proyecto anterior de un solo módulo. Se trasladó el Wrapper a `backend/` y se conservó el documento anterior en `docs/referencia/entrega-version-inicial.md`.

PR1 incorporó:

- `reclamos-comun`: `Validador` (texto, longitudes, DNI, email, no nulos y positivos) y la jerarquía `ReclamosException`, `DatosInvalidosException`, `AccesoDenegadoException`, `RecursoNoEncontradoException` y `ReglaNegocioException`.
- Modelo: `Entidad`, `Usuario`, `Ciudadano`, `AgenteMunicipal`, `Administrador`, `Municipio`, `Barrio`, `AreaMunicipal`, `Categoria`, `Reclamo`, `HistorialEstado` y `Notificacion`.
- Enums: `EstadoReclamo`, `Prioridad`, `Rol` y `CanalNotificacion`. `Rol.SISTEMA` representa operaciones automáticas, sin usuario autenticable.
- `PoliticaTransiciones`, fuente única de transiciones y roles; `Reclamo` agrega controles de pertenencia, asignación y registro de historial.
- `ReclamoFactory`: número `REC-` más ocho caracteres hexadecimales en mayúsculas, estado `INGRESADO`, fecha truncada a segundos, fecha límite según SLA, prioridad base y primer historial. Recibe `Clock` para controlar el tiempo en pruebas; rechaza crear para ciudadanos inactivos.
- Siete puertos Repository: `UsuarioRepository`, `MunicipioRepository`, `BarrioRepository`, `CategoriaRepository`, `AreaMunicipalRepository`, `ReclamoRepository` y `NotificacionRepository`.
- Contratos `EstrategiaPrioridad`, `EstrategiaAsignacion`, `EventoDominio` y `PublicadorEventos`; eventos `ReclamoCreado`, `ReclamoAsignado`, `EstadoReclamoCambiado`, `ReclamoResuelto` y `ReclamoVencido`.
- `docs/api-contract.md`, con rutas, objetos, permisos, errores y semilla acordada; tests iniciales de validación, modelo, transiciones y Factory, además del test de contexto de aplicación.

Factory y reglas de dominio quedaron implementadas. Repository, Strategy y Observer quedaron preparados mediante interfaces y eventos; la Facade concreta se incorpora en PR3. PR1 también creó Compose para MySQL 8.4 y los perfiles iniciales `dev`/`test`; PR2 completó su conexión parametrizada y la persistencia. Estos archivos de infraestructura no nacieron íntegramente en PR2.

El dominio ya dispone de `Reclamo.marcarVencido`, incremento de prioridad y `ReclamoVencido`. Su existencia no implica que haya un proceso automático de vencimientos en PR3.

## 4. PR2 — Persistencia + Repository + MySQL

El commit `2c27785` implementa los puertos del dominio con Spring Data JPA. Por ejemplo, `ReclamoRepositoryJpa` implementa `ReclamoRepository` y delega en `ReclamoJpaRepository`; la misma separación existe para usuarios, municipios, barrios, categorías, áreas y notificaciones.

`backend/reclamos-persistencia/src/main/resources/META-INF/orm.xml` define acceso a campos e IDs `IDENTITY`, sin anotaciones JPA en dominio:

| Relación o decisión | Mapping real |
| --- | --- |
| Usuarios y subtipos | Herencia `SINGLE_TABLE` en `usuarios`, discriminador `tipo`. |
| Barrio / área a municipio | `many-to-one` LAZY. |
| Agente a área | LAZY; columna nullable porque comparte tabla con otros roles. |
| Área a barrios y categorías | `many-to-many` LAZY en `area_barrio` y `area_categoria`, pares únicos. |
| Reclamo a ciudadano, categoría y barrio | Referencias obligatorias LAZY. |
| Reclamo a área y agente | Referencias opcionales LAZY. |
| Reclamo a historial | `one-to-many`, cascadas persist/merge, orden `fecha ASC, id ASC`. |
| Notificación | Referencias obligatorias a reclamo y destinatario. |

El número de reclamo y DNI de usuario tienen restricciones de unicidad. Los enums se almacenan como texto. `CargadorRelaciones` inicializa relaciones necesarias dentro de la transacción y elimina proxies de usuarios cuando corresponde; los repositorios también usan `@EntityGraph` para consultas concretas. Esto permite consumir las relaciones después de finalizar la transacción con **`open-in-view=false`**, sin depender de una sesión abierta durante la serialización HTTP.

PR2 añadió `.env.example`, parametrizó Compose y `application-dev.yml`, incorporó `db/datos-dev.sql` y los tests de persistencia. MySQL Docker es la base de `dev`; H2 en modo MySQL es la base de tests. `dev` usa `ddl-auto: update` y difiere la inicialización SQL hasta crear/actualizar el esquema. Los tests usan `create-drop`; el módulo de persistencia desactiva la inicialización SQL automática.

La semilla utiliza la tabla `inicializaciones` con marcador `datos-dev-v1`: el script puede invocarse en cada arranque, pero solo inserta datos antes de registrar el marcador. Los reinicios no restauran registros o relaciones que se modificaron después. Los tests verifican guardado/recuperación tras flush/clear, actualizaciones, unicidad, consultas, semilla repetible y relaciones fuera de transacción.

## 5. PR3 — Aplicación + patrones + REST

### Strategy

| Clase | Algoritmo actual |
| --- | --- |
| `PrioridadPorCategoria` | Devuelve la prioridad base de la categoría. |
| `PrioridadPorPalabrasClave` | Busca palabras completas `urgente`, `peligro`, `riesgo` y `accidente` en la descripción, sin distinguir mayúsculas. Ante una coincidencia devuelve el máximo entre prioridad base y `ALTA`; de lo contrario conserva la base. |
| `AsignacionPorJurisdiccion` | Selecciona el menor ID entre las áreas candidatas; sin candidatas devuelve vacío. |
| `AsignacionPorCargaDeTrabajo` | Selecciona la menor cantidad de reclamos `ASIGNADO` o `EN_PROCESO` del área; desempata por menor ID. Con una candidata la devuelve directamente, sin consultar cargas; sin candidatas devuelve vacío. |

La búsqueda de palabras usa límites que excluyen letras, números y guion bajo adyacentes. No interpreta negaciones, plurales ni contexto; múltiples coincidencias no acumulan incrementos y `CRITICA` nunca se reduce.

Las dos Strategies de asignación reciben candidatas ya filtradas por `AreaMunicipalRepository.buscarCandidatas`: áreas activas que atienden la categoría y cubren el barrio. No calculan distancias ni consultan un servicio territorial externo.

### Configuración

`PropiedadesReclamos` vincula el prefijo `reclamos`; `ConfiguracionAplicacion` registra exactamente una implementación por contrato:

```yaml
reclamos:
  prioridad:
    estrategia: categoria        # categoria | palabras-clave
  asignacion:
    estrategia: jurisdiccion     # jurisdiccion | carga-trabajo
```

Los valores predeterminados reales en `application.yml` son `categoria` y `jurisdiccion`. Una selección desconocida o vacía impide arrancar con `IllegalArgumentException`. La configuración también registra `Clock.systemDefaultZone()` si no existe otro bean `Clock`, y crea el bean `ReclamoFactory` con ese reloj. Las fechas REST son `LocalDateTime`, sin zona; el reloj toma la zona de la JVM.

### Servicios

| Clase | Casos de uso |
| --- | --- |
| `ReclamoService` | Crear, asignar/reasignar, cambiar estado, tomar, resolver, cerrar, reabrir, rechazar y cancelar; coordina Factory, Strategies, guardado y eventos. |
| `ConsultaReclamosService` | Listar según rol, aplicar filtros, buscar por número, consultar notificaciones y calcular acciones disponibles. |
| `ConsultaCatalogosService` | Consultar usuarios, usuario por ID, categorías, barrios y áreas. |
| `PermisosReclamo` | Exigir ciudadano al crear, verificar visibilidad y restringir filtros administrativos; combina visibilidad con `PoliticaTransiciones` para las acciones. |

Las transiciones y la pertenencia en escrituras se validan en `Reclamo`; los servicios no duplican la tabla de estados. Las consultas seleccionan primero por ciudadano/área o todos según el actor, mantienen orden descendente de creación con desempate por ID, y completan los filtros con streams en memoria.

### Facade

`GestionReclamosFacade` es el punto de entrada de aplicación para los tres controllers. Delega en servicios de escritura, consultas y catálogos, con métodos independientes de HTTP. REST traduce entrada/salida, mientras que la coordinación de casos de uso queda detrás de la Facade. La fachada no define transacciones propias.

### Observer

`PublicadorEventosSpring` implementa `PublicadorEventos` usando `ApplicationEventPublisher`. La configuración actual utiliza eventos Spring **síncronos**: los listeners se ejecutan durante la llamada del caso de uso, antes del commit.

`NotificacionesReclamoListener` usa `@EventListener` y `@Transactional(propagation = Propagation.MANDATORY)`: exige una transacción existente y participa de la del servicio. Recupera el reclamo y guarda `Notificacion` mediante el puerto Repository, siempre con canal **`INTERNO`**.

| Evento | Qué representa | A quién notifica |
| --- | --- | --- |
| `ReclamoCreado` | Ingreso del reclamo. | Ciudadano propietario. |
| `ReclamoAsignado` | Asignación automática, manual o reasignación. | Ciudadano propietario; no avisa a todos los agentes del área. |
| `EstadoReclamoCambiado` | Cambio solicitado por usuario. | Ciudadano, salvo destino `RESUELTO`; si el origen es `RESUELTO`, también al agente registrado cuando existe (reapertura o cierre). |
| `ReclamoResuelto` | Resolución por agente, pendiente de confirmación. | Ciudadano propietario. |

Al resolver, el servicio publica `EstadoReclamoCambiado` y `ReclamoResuelto`. El listener del cambio genérico omite destino `RESUELTO`, de modo que la resolución genera **un solo aviso**. Al asignar se publica `ReclamoAsignado`, sin publicar además el evento genérico por esa misma operación.

Si el listener falla con una excepción de ejecución, el error se propaga y se revierte la transacción compartida, incluyendo reclamo, historial y notificaciones previamente guardadas en ese caso de uso. Los tests de integración comprueban este rollback. `ReclamoVencido` no tiene publicación desde los servicios actuales ni listener de notificación implementado.

### REST

`ReclamoController`, `UsuarioController` y `CatalogoController` entran por `GestionReclamosFacade`. `RestMapper` transforma el dominio a DTOs, evitando serializar entidades JPA o relaciones inversas.

Los requests son `CrearReclamoRequest`, `CambiarEstadoRequest` y `AsignarReclamoRequest`. Las respuestas incluyen `ReclamoResponse`, `ResumenReclamoResponse`, `HistorialEstadoResponse`, `NotificacionResponse`, `UsuarioResponse`, `CategoriaResponse`, `BarrioResponse`, `AreaResponse` y referencias `ReferenciaResponse`/`UsuarioReferenciaResponse`.

| Entrada | Validación REST |
| --- | --- |
| Crear | `categoriaId` y `barrioId` obligatorios y positivos; `descripcion` no vacía hasta 1000 caracteres; `direccion` no vacía hasta 200. |
| Cambiar estado | `estado` obligatorio; `observacion` opcional hasta 1000 caracteres. `ASIGNADO` se rechaza por esta operación. |
| Asignar | `areaId` obligatorio y positivo; `observacion` opcional hasta 1000 caracteres. |
| Identidad y filtros ID | `X-Usuario-Id`, `areaId`, `ciudadanoId` e ID de consulta de usuario positivos, cuando corresponden. |

`ConfiguracionJsonRest` rechaza enums enviados como números y evita convertir números decimales a enteros. `ManejadorGlobalErrores` uniforma fallos de negocio, validación y transporte en `ErrorResponse` (`status`, `codigo`, `mensaje`, `detalles`, `ruta`, `fecha`).

| HTTP | Código | Causa típica |
| --- | --- | --- |
| 400 | `DATOS_INVALIDOS` | JSON, encabezado, parámetros o cuerpo inválidos. |
| 403 | `ACCESO_DENEGADO` | Rol o pertenencia no habilitados. |
| 404 | `RECURSO_NO_ENCONTRADO` | Recurso o ruta inexistente. |
| 409 | `REGLA_NEGOCIO` | Transición inexistente u otra regla incumplida. |
| 405 | `METODO_NO_PERMITIDO` | Método HTTP no soportado por el recurso. |
| 415 | `TIPO_CONTENIDO_NO_SOPORTADO` | Content-Type no soportado. |
| 500 | `ERROR_INTERNO` | Excepción inesperada registrada en logs, con mensaje genérico al cliente. |

La respuesta detallada incorpora historial y `accionesDisponibles` según el usuario; el listado usa resúmenes sin descripción completa, historial ni agente. POST devuelve 201 y `Location`; consultas y PATCH exitosos devuelven 200.

## 6. Flujo completo de creación

```text
POST /api/reclamos + X-Usuario-Id
  ↓ ReclamoController: validar CrearReclamoRequest
  ↓ GestionReclamosFacade.crear
  ↓ ReclamoService.crear: iniciar transacción
  ↓ buscar usuario y validar ciudadano; buscar categoría y barrio
  ↓ ReclamoFactory: número, INGRESADO, fechas, SLA e historial inicial
  ↓ EstrategiaPrioridad: definir prioridad
  ↓ AreaMunicipalRepository: buscar áreas candidatas
  ↓ EstrategiaAsignacion: elegir área
  ↓ si existe área: asignar automáticamente y agregar historial
  ↓ ReclamoRepository.guardar
  ↓ publicar ReclamoCreado y, si hubo asignación, ReclamoAsignado
  ↓ listeners síncronos: guardar Notificacion
  ↓ commit de la transacción del servicio
  ↓ calcular acciones y mapear respuesta
  ↓ 201 Created + Location
```

Si no hay área candidata, no es un error: el reclamo queda `INGRESADO`, sin área ni agente, se publica solo `ReclamoCreado` y se responde 201. La asignación manual posterior puede dirigirse a cualquier área activa distinta de la actual; el dominio no exige que atienda esa categoría o barrio, a diferencia del filtro automático.

## 7. Ciclo de vida del reclamo

Estados reales: `INGRESADO`, `ASIGNADO`, `EN_PROCESO`, `RESUELTO`, `CERRADO`, `RECHAZADO` y `CANCELADO`.

La siguiente tabla reproduce `PoliticaTransiciones`. Los controles de propietario y área los agrega `Reclamo`:

| Origen | Destino | Rol / actor | Operación |
| --- | --- | --- | --- |
| INGRESADO | ASIGNADO | SISTEMA o ADMINISTRADOR | Asignación automática o manual. |
| INGRESADO | RECHAZADO | ADMINISTRADOR | Rechazar. |
| INGRESADO | CANCELADO | CIUDADANO propietario | Cancelar. |
| ASIGNADO | ASIGNADO | ADMINISTRADOR | Reasignar a otra área activa. |
| ASIGNADO | EN_PROCESO | AGENTE_MUNICIPAL del área | Tomar; se registra ese agente. |
| ASIGNADO | CANCELADO | CIUDADANO propietario | Cancelar. |
| EN_PROCESO | RESUELTO | AGENTE_MUNICIPAL del área | Resolver. |
| RESUELTO | CERRADO | CIUDADANO propietario | Confirmar cierre. |
| RESUELTO | EN_PROCESO | CIUDADANO propietario | Reabrir. |

No hay otras transiciones. `CERRADO`, `RECHAZADO` y `CANCELADO` son finales. `RESUELTO` aún permite confirmar o reabrir. Una transición inexistente produce 409 antes de revisar rol/pertenencia; una transición existente sin permiso produce 403. Para destino `ASIGNADO` se usa la operación de asignación, no cambio genérico de estado.

## 8. Roles y permisos

| Rol de usuario | Consulta | Escritura |
| --- | --- | --- |
| CIUDADANO | Sus reclamos, detalle y avisos. | Crear si está activo, cancelar ingresados/asignados, cerrar o reabrir sus resueltos. |
| AGENTE_MUNICIPAL | Reclamos del área a la que pertenece, detalle y avisos. | Tomar asignados y resolver en proceso de su área. |
| ADMINISTRADOR | Todos los reclamos; puede filtrar por área y ciudadano. | Asignar ingresados, reasignar asignados y rechazar ingresados. No crea reclamos ni recibe todas las transiciones por ser administrador. |

`SISTEMA` es un rol técnico para acciones automáticas, sin usuario para el selector. La resolución verifica pertenencia al área, **no exige que el actor sea exactamente el agente que tomó el reclamo**. La reapertura conserva el agente registrado.

`X-Usuario-Id` es autenticación simulada del Hito 1: cualquier cliente puede declarar el ID de un usuario existente. No hay verificación de credenciales ni JWT. Usuarios y catálogos se consultan sin ese encabezado. El endpoint de notificaciones aplica visibilidad del reclamo y devuelve todos sus avisos, sin filtrar exclusivamente por destinatario.

## 9. API actual

| Método | Ruta | Descripción | Actor |
| --- | --- | --- | --- |
| POST | `/api/reclamos` | Crear y evaluar asignación automática. | Ciudadano. |
| GET | `/api/reclamos` | Listar resúmenes, del más nuevo al más viejo. | Tres roles, con visibilidad propia. |
| GET | `/api/reclamos/{numero}` | Detalle, historial y acciones. | Tres roles, con visibilidad propia. |
| PATCH | `/api/reclamos/{numero}/estado` | Tomar, resolver, cerrar, reabrir, rechazar o cancelar. | Según transición y pertenencia. |
| PATCH | `/api/reclamos/{numero}/asignacion` | Asignar o reasignar área, dejando agente nulo. | Administrador. |
| GET | `/api/reclamos/{numero}/notificaciones` | Consultar avisos persistidos. | Tres roles, con visibilidad propia. |
| GET | `/api/usuarios` | Listar selector de usuarios. | Sin encabezado de identidad. |
| GET | `/api/usuarios/{id}` | Consultar usuario. | Sin encabezado de identidad. |
| GET | `/api/categorias` | Consultar categorías, SLA y prioridad base. | Sin encabezado de identidad. |
| GET | `/api/barrios` | Consultar barrios y municipio. | Sin encabezado de identidad. |
| GET | `/api/areas` | Consultar áreas y sus relaciones de cobertura. | Sin encabezado de identidad. |

Las seis rutas de reclamos exigen `X-Usuario-Id`. El listado permite `estado` para todos y `areaId`/`ciudadanoId` únicamente para administrador. Filtros con IDs positivos inexistentes devuelven `[]`. No hay paginación ni endpoints independientes para historial, acciones, toma o resolución: se usan detalle y cambio de estado.

## 10. Patrones de diseño implementados

| Patrón | Ubicación concreta | Función |
| --- | --- | --- |
| Factory | `ReclamoFactory` en dominio; bean en `ConfiguracionAplicacion`. | Centralizar inicialización coherente del reclamo y cálculo de SLA. Es una fábrica concreta; no están las fábricas normal/urgente de la versión histórica. |
| Repository | Interfaces en `dominio/repositorio`, adaptadores `*RepositoryJpa` e interfaces `*JpaRepository` en persistencia. | Abstraer acceso a datos y permitir servicios que dependan de puertos. |
| Strategy | `EstrategiaPrioridad`/`EstrategiaAsignacion` y sus cuatro implementaciones de aplicación. | Elegir algoritmos intercambiables mediante configuración. |
| Observer | `PublicadorEventos`, `PublicadorEventosSpring`, eventos y `NotificacionesReclamoListener`. | Reaccionar a hechos del caso de uso sin acoplar el servicio a la generación de avisos. |
| Facade | `GestionReclamosFacade`. | Ofrecer una entrada común a escritura, consultas y catálogos desde REST. |

`PoliticaTransiciones` usa una tabla de reglas; no implementa el patrón State.

## 11. Transacciones

Cada método público de escritura de `ReclamoService` tiene `@Transactional`. `ConsultaReclamosService` y `ConsultaCatalogosService` tienen `@Transactional(readOnly = true)`. Los adaptadores de persistencia también declaran consultas read-only y escrituras transaccionales, uniéndose a la transacción existente cuando los llama el servicio.

El Observer síncrono comparte la transacción por `Propagation.MANDATORY`; no hay `@Async`, broker ni listener posterior al commit. Ante una excepción de ejecución, reclamo, cambios de agente/estado, historial y notificaciones de la operación se revierten conjuntamente. Los tests de integración no envuelven todo el caso en una transacción de test, para comprobar commits y rollback reales.

`open-in-view=false` cierra el contexto transaccional antes de mapear la respuesta REST. Los adaptadores cargan previamente las relaciones requeridas. La Facade y los controllers no amplían la transacción de escritura hasta el final de la respuesta HTTP.

## 12. Testing

Los **27 reportes Surefire XML existentes**, generados el 03/10/2026 entre 06:20:30 y 06:20:46 según las marcas locales de archivos, registran **221 tests**, 0 fallos, 0 errores y 0 omitidos. Se incluyen las ejecuciones parametrizadas. No se volvió a ejecutar Maven para generar esta documentación, evitando escribir artefactos fuera de los dos Markdown autorizados; el conteo corresponde a esos reportes y no certifica una ejecución nueva.

| Cobertura | Evidencia en clases de test |
| --- | --- |
| Validación y dominio | `ValidadorTest`, `EntidadTest`, `AreaMunicipalTest`, `ReclamoTest`, `PoliticaTransicionesTest`. |
| Factory | `ReclamoFactoryTest`: número, inicialización, SLA, reloj y validaciones. |
| Strategy y configuración | `PrioridadTest`, `AsignacionTest`, `ConfiguracionAplicacionTest`: algoritmos, límites de palabras, desempates, selección de beans y configuración inválida. |
| Persistencia | `ReclamoRepositoryJpaTest`, `CatalogosYUsuariosRepositoryJpaTest`, `AreaMunicipalRepositoryJpaTest`, `NotificacionRepositoryJpaTest`. |
| Semilla y LAZY | `DatosSemillaTest`, `RelacionesFueraDeTransaccionTest`. |
| Servicios y permisos | `ReclamoServiceTest`, `PermisosReclamoTest`, `ConsultaReclamosServiceTest`, `ConsultaCatalogosServiceTest`. |
| Facade y límites de transacción | `GestionReclamosFacadeTest`, `TransaccionesServiceTest`. |
| Integración de servicios/consultas | `ServiciosAplicacionIntegrationTest`, `ConsultaReclamosServiceIntegrationTest`: persistencia entre transacciones, orden, filtros y rollback. |
| Observer | `PublicadorEventosSpringTest`, `ObserverIntegrationTest`: avisos, ausencia de duplicados y rollback ante fallas del listener. |
| REST con MockMvc | `RestIntegrationTest`: rutas, DTOs, validaciones, roles, filtros, errores y flujo con persistencia H2. |
| Contexto | `ReclamosApplicationTests` con perfil `test`. |

Se utilizan JUnit Jupiter, Mockito, AssertJ y herramientas de test Spring. `ConfiguracionPersistenciaTest` es una clase de configuración para las pruebas, no una suite adicional con métodos `@Test`.

Las integraciones usan H2 en memoria en modo MySQL; no dependen de Docker. La evidencia de H2 no reemplaza una prueba de arranque o ejecución contra MySQL en otra PC. Los comandos para ejecutar la suite están en el instructivo.

## 13. Bases de datos

| Entorno | Recorrido | Configuración |
| --- | --- | --- |
| Desarrollo | Spring Boot local → MySQL Docker. | `application-dev.yml`, imagen `mysql:8.4`, puerto host `DB_PORT` (3306 por defecto), puerto interno 3306, volumen `reclamos-mysql-data`. |
| Tests | Spring Boot/DataJpaTest → H2 en memoria. | Perfil `test`, modo MySQL, esquema `create-drop`; escenarios preparados por las pruebas. |

`dev` usa `DB_USER`, `DB_PASSWORD` y la URL construida con `DB_NAME`/`DB_PORT`; `DB_URL`, si existe, reemplaza la URL completa. `.env.example` contiene `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` y `DB_PORT`. Compose interpreta `.env`; el Spring local necesita esas variables en su entorno.

La semilla `db/datos-dev.sql` contiene un municipio (Quilmes), barrios 1 Quilmes Centro, 2 Bernal y 3 Ezpeleta; categorías 1 Luminaria rota (48 h, MEDIA), 2 Bache (120 h, BAJA) y 3 Residuos (72 h, MEDIA).

| Área semilla | Categoría | Jurisdicción |
| --- | --- | --- |
| 1 Alumbrado | Luminaria rota | Quilmes Centro, Bernal. |
| 2 Obras Públicas | Bache | Quilmes Centro, Bernal, Ezpeleta. |
| 3 Higiene Urbana | Residuos | Quilmes Centro, Bernal. |
| 4 Mantenimiento Vial | Bache | Bernal. |

Los usuarios 1 y 2 son ciudadanos; 3, 4, 5 y 6 son agentes de las áreas 1, 2, 3 y 4 respectivamente; 7 es administrador. Los nombres y ejemplos operativos figuran en el instructivo. No se precargan reclamos ni notificaciones.

El marcador `datos-dev-v1` en `inicializaciones` evita reponer datos modificados o eliminados después de inicializar. El volumen conserva tanto datos como credenciales MySQL: cambiar `.env` no actualiza automáticamente las credenciales existentes.

## 14. Qué NO está implementado todavía

En el árbol actual y sus dependencias no se encontraron implementaciones de:

- SOAP/WSDL ni cliente de jurisdicción remota.
- Broker real RabbitMQ/Kafka.
- IA/LLM para prioridad u otros casos de uso.
- API externa de geolocalización o mapas.
- JWT/login y autenticación real.
- Microservicios: los módulos integran una aplicación Spring Boot, no procesos independientes.
- Frontend React: no existe carpeta `frontend/` ni proyecto React en el árbol inspeccionado.
- Scheduler automático de vencimientos: hay soporte en dominio y Repository, pero no ejecución periódica ni caso de uso que lo active.
- Optimistic locking: no hay atributo de versión ni mapping de versión en `orm.xml`.

Los comentarios de los contratos Strategy y eventos prevén SOAP, IA y broker para Hito 2. No se encontró una consigna completa independiente que permita verificar todos los compromisos de entregas futuras; las ausencias anteriores están confirmadas contra el código y configuración disponibles.

## 15. Limitaciones conocidas / decisiones

- `X-Usuario-Id` permite demostrar roles y permisos del Hito 1 con identidad simulada.
- Las escrituras son transaccionales, pero operaciones concurrentes sobre el mismo reclamo no cuentan con optimistic locking ni bloqueos explícitos que prevengan sobrescrituras.
- Los filtros se completan parcialmente en memoria después de seleccionar por rol/área/ciudadano. No hay paginación; es una decisión de alcance para el volumen de la entrega.
- Las notificaciones son registros internos, sin entregas reales por EMAIL o SMS, aunque esos valores existan en el enum. La asignación notifica al ciudadano; cierre/reapertura también al agente registrado.
- La prioridad por palabras clave es determinista; no es IA y no interpreta lenguaje natural más allá de las coincidencias documentadas.
- `vencido` es un atributo persistido que modifica `marcarVencido`; consultar un reclamo no recalcula automáticamente su vencimiento.

### Diferencias encontradas con la documentación previa

El `README.md` existente anuncia `frontend/`, React/Vite y Node.js, pero esos archivos no existen; su tabla de trabajo llama a la rama PR3 `feature/03-business-rest`, mientras que Git muestra `feature/03-services-rest`. Esa rama aún no contiene un commit de PR3.

`docs/api-contract.md` sigue siendo compatible con el núcleo de las rutas y DTOs actuales, pero su tabla general de errores no incluye 405 y 415, implementados en `ManejadorGlobalErrores`, y algunos límites adicionales de validación (IDs positivos, observación de hasta 1000 caracteres) están definidos en código. Su ejemplo de notificación usa canal EMAIL y un mensaje ilustrativo; el listener real persiste INTERNO y textos propios. El comentario de `UsuarioRepository.buscarAgentesPorArea` sugiere avisar a agentes cuando entra un reclamo, pero el Observer actual no usa ese método para creación/asignación.

`docs/referencia/entrega-version-inicial.md` documenta clases anteriores (`ReclamoFacade`, `CiudadanoService`, fábricas normal/urgente), repositorios en memoria y rutas `/api/v1/reclamos`; está conservado como referencia histórica y no representa el backend actual. Esta documentación describe los nombres y rutas verificados sin modificar esos archivos previos.

No se verificaron en esta revisión un nuevo arranque contra MySQL, respuestas HTTP en vivo, credenciales de un volumen existente ni disponibilidad remota de PR3. Los comportamientos se documentan desde código, configuración, historial local y reportes de tests existentes.

## 16. Estado al finalizar PR3

Con los archivos actuales de PR3 presentes y la configuración de desarrollo preparada, el backend permite crear reclamos, calcular prioridad, asignarlos automáticamente cuando hay cobertura, asignar/reasignar manualmente, tomarlos y cambiar estados conforme al rol. También permite consultar listados por rol, detalle con historial y acciones, y notificaciones internas generadas por eventos.

Los casos de uso se exponen por los 11 endpoints REST documentados y persisten mediante JPA en MySQL para desarrollo. La suite registrada cubre dominio, patrones, servicios, persistencia y REST con H2. PR1 y PR2 están integrados; la implementación de PR3 está disponible en el working tree inspeccionado y su commit/publicación todavía no están verificados.
