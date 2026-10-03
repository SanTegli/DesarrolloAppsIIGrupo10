# Arquitectura

El sistema es una aplicación en capas: cada capa solo conoce a la que tiene debajo, y el dominio no
depende de ningún framework.

## Capas

```mermaid
flowchart TB
  subgraph UI["Interfaz - frontend/ (React + Vite)"]
    COMP["Componentes: listado, alta, detalle, administración"]
    API["src/api: cliente HTTP y X-Usuario-Id"]
  end
  subgraph REST["Presentación REST - reclamos-app/aplicacion/rest"]
    CTRL["ReclamoController, UsuarioController, CatalogoController"]
    DTO["DTOs, RestMapper, ManejadorGlobalErrores"]
  end
  subgraph APP["Servicios - reclamos-app/aplicacion"]
    FAC["GestionReclamosFacade"]
    SRV["ReclamoService, ConsultaReclamosService, ConsultaCatalogosService"]
    PERM["PermisosReclamo"]
    EST["Estrategias de prioridad y asignación"]
    OBS["PublicadorEventosSpring y NotificacionesReclamoListener"]
  end
  subgraph DOM["Dominio - reclamos-dominio (Java puro)"]
    MOD["Modelo y PoliticaTransiciones"]
    FAB["ReclamoFactory"]
    CON["Contratos: Repository, Estrategia, PublicadorEventos, eventos"]
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

  FE -->|"REST /api"| APP
  APP --> PERS
  APP --> DOM
  PERS --> DOM
  DOM --> COM
  PERS --> MYSQL
```

## Servicios orientados a componentes

| Servicio | Responsabilidad |
| --- | --- |
| `ReclamoService` | Casos de uso de escritura: crear, asignar, reasignar y cambiar de estado |
| `ConsultaReclamosService` | Listar según el rol, buscar por número, avisos y acciones disponibles |
| `ConsultaCatalogosService` | Usuarios, categorías, barrios y áreas |
| `PermisosReclamo` | Quién puede crear y quién puede ver cada reclamo |

Los servicios se integran entre sí de dos formas:

- **Por interfaces.** Dependen de los contratos del dominio (`ReclamoRepository`,
  `EstrategiaPrioridad`, `EstrategiaAsignacion`, `PublicadorEventos`) y Spring inyecta la
  implementación.
- **Por eventos de dominio.** `ReclamoService` publica `ReclamoCreado`, `ReclamoAsignado`,
  `EstadoReclamoCambiado` y `ReclamoResuelto`; `NotificacionesReclamoListener` reacciona sin que
  el servicio lo conozca.

## Decisiones de diseño

| Decisión | Motivo |
| --- | --- |
| Mapeo JPA en `META-INF/orm.xml` en lugar de anotaciones | El dominio queda sin ninguna dependencia de JPA y se prueba con JUnit solo |
| Interfaces Repository en el dominio, adaptadores en persistencia | Los servicios no dependen de `JpaRepository`; en los tests se reemplazan por dobles en memoria |
| Transiciones en una tabla (`PoliticaTransiciones`) y no con el patrón State | Nueve transiciones se leen mejor en una tabla que repartidas en siete clases |
| Identidad con el encabezado `X-Usuario-Id` | El Hito 1 no pide autenticación; el rol sale del usuario guardado en la base |
| Eventos de Spring síncronos, dentro de la transacción | Si un listener falla se revierte todo el caso de uso; no quedan reclamos sin aviso |
| El frontend llama a `/api` en su mismo origen y un proxy reenvía al backend | No hace falta configurar CORS en Spring; funciona igual con Vite y con nginx |
| `accionesDisponibles` en la respuesta del reclamo | La interfaz no repite las reglas de transición: muestra un botón por cada acción |
| `open-in-view: false` y DTOs en REST | No se serializan entidades JPA ni se abren consultas durante la respuesta HTTP |

## Preparado para el Hito 2

Ninguna de estas integraciones está implementada; el diseño deja el lugar donde entran.

| Integración | Dónde entra | Qué se agrega |
| --- | --- | --- |
| Clasificación con IA | `EstrategiaPrioridad` | Una clase `PrioridadPorIA` y un valor más en la configuración |
| Servicio SOAP de jurisdicción | `EstrategiaAsignacion` | Una clase `AsignacionPorJurisdiccionRemota` |
| Broker de mensajes | `PublicadorEventos` y los eventos de dominio | Un listener que reenvía los mismos eventos a RabbitMQ |
| Geolocalización | `ReclamoService.crear` | Un cliente detrás de una interfaz y dos columnas en `Reclamo` |

## Pendientes conocidos

- **Vencimiento de reclamos.** El dominio tiene `Reclamo.marcarVencido` y el evento `ReclamoVencido`,
  y el Repository tiene `buscarVencidosSinMarcar`, pero ningún proceso los ejecuta todavía.
- **Avisos reales.** Las notificaciones se guardan en la base con canal `INTERNO`; no se envían
  correos ni SMS.
