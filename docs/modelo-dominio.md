# Modelo de dominio

El dominio está en el módulo `reclamos-dominio`, paquete `ar.edu.uade.reclamos.dominio.modelo`. Son
clases Java sin anotaciones: el mapeo a tablas está aparte, en
`reclamos-persistencia/src/main/resources/META-INF/orm.xml`.

## Clases

`Reclamo` es la clase central: protege su estado, valida cada cambio contra `PoliticaTransiciones`
y registra su propio historial.

```mermaid
classDiagram
  direction LR
  class Entidad {
    <<abstract>>
    -Long id
    +equals(Object) boolean
  }
  class Usuario {
    <<abstract>>
    -String dni
    -String nombre
    -String apellido
    -String email
    -boolean activo
    +getRol() Rol
    +getNombreCompleto() String
  }
  class Ciudadano
  class Administrador
  class AgenteMunicipal {
    +perteneceA(AreaMunicipal) boolean
  }
  class Municipio {
    -String nombre
    -String provincia
  }
  class Barrio {
    -String nombre
  }
  class Categoria {
    -String nombre
    -int slaHoras
    -Prioridad prioridadBase
  }
  class AreaMunicipal {
    -String nombre
    -boolean activa
    +atiende(Categoria) boolean
    +tieneJurisdiccionSobre(Barrio) boolean
    +puedeAtender(Categoria, Barrio) boolean
  }
  class Reclamo {
    -String numero
    -String descripcion
    -String direccion
    -EstadoReclamo estado
    -Prioridad prioridad
    -boolean vencido
    -LocalDateTime fechaCreacion
    -LocalDateTime fechaLimite
    +asignarArea(AreaMunicipal, Usuario, String, LocalDateTime)
    +cambiarEstado(EstadoReclamo, Usuario, String, LocalDateTime)
    +marcarVencido(LocalDateTime) boolean
    +definirPrioridad(Prioridad)
    +perteneceA(Usuario) boolean
  }
  class HistorialEstado {
    -EstadoReclamo estadoAnterior
    -EstadoReclamo estadoNuevo
    -LocalDateTime fecha
    -String observacion
    +fueAutomatico() boolean
  }
  class Notificacion {
    -CanalNotificacion canal
    -String mensaje
    -LocalDateTime fechaEnvio
  }
  class PoliticaTransiciones {
    <<utility>>
    +validar(EstadoReclamo, EstadoReclamo, Rol)$
    +destinosPosibles(EstadoReclamo, Rol)$ Set
  }
  class ReclamoFactory {
    +crear(Ciudadano, Categoria, Barrio, String, String) Reclamo
  }

  Entidad <|-- Usuario
  Entidad <|-- Reclamo
  Entidad <|-- AreaMunicipal
  Usuario <|-- Ciudadano
  Usuario <|-- AgenteMunicipal
  Usuario <|-- Administrador
  Municipio "1" <-- "*" Barrio
  Municipio "1" <-- "*" AreaMunicipal
  AreaMunicipal "*" --> "*" Barrio : jurisdicción
  AreaMunicipal "*" --> "*" Categoria : atiende
  AgenteMunicipal "*" --> "1" AreaMunicipal
  Reclamo "*" --> "1" Ciudadano
  Reclamo "*" --> "1" Categoria
  Reclamo "*" --> "1" Barrio
  Reclamo "*" --> "0..1" AreaMunicipal
  Reclamo "*" --> "0..1" AgenteMunicipal
  Reclamo "1" *-- "1..*" HistorialEstado
  HistorialEstado "*" --> "0..1" Usuario
  Notificacion "*" --> "1" Reclamo
  Notificacion "*" --> "1" Usuario : destinatario
  Reclamo ..> PoliticaTransiciones : valida con
  ReclamoFactory ..> Reclamo : crea
```

`Municipio`, `Barrio`, `Categoria`, `HistorialEstado` y `Notificacion` también heredan de `Entidad`;
no se dibuja para no cargar el diagrama.

| Enum | Valores |
| --- | --- |
| `EstadoReclamo` | `INGRESADO`, `ASIGNADO`, `EN_PROCESO`, `RESUELTO`, `CERRADO`, `RECHAZADO`, `CANCELADO` |
| `Prioridad` | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `Rol` | `CIUDADANO`, `AGENTE_MUNICIPAL`, `ADMINISTRADOR`, `SISTEMA` |
| `CanalNotificacion` | `EMAIL`, `SMS`, `INTERNO` |

`Rol.SISTEMA` no corresponde a ningún usuario: representa las acciones automáticas, como la
asignación al crear el reclamo. En el Hito 1 todos los avisos usan el canal `INTERNO`.

## Reglas que viven en el dominio

| Regla | Dónde está |
| --- | --- |
| Qué transiciones de estado existen y qué rol puede ejecutar cada una | `PoliticaTransiciones` |
| Un ciudadano solo opera sobre sus reclamos; un agente, sobre los de su área | `Reclamo.cambiarEstado` |
| Solo se asigna a un área activa y distinta de la actual | `Reclamo.asignarArea` |
| Un área puede atender si está activa, atiende la categoría y cubre el barrio | `AreaMunicipal.puedeAtender` |
| Un reclamo vence una sola vez y sube un nivel de prioridad | `Reclamo.marcarVencido` |
| Todo reclamo nace `INGRESADO`, con número, fecha límite e historial | `ReclamoFactory.crear` |

El ciclo de vida completo está en [secuencias.md](secuencias.md#ciclo-de-vida-del-reclamo).

## Tablas

Tablas y columnas tal como las define `orm.xml`. Los tres tipos de usuario comparten la tabla
`usuarios` y se distinguen por la columna `tipo`. Las relaciones muchos a muchos usan las tablas
`area_barrio` y `area_categoria`.

```mermaid
erDiagram
  municipios ||--o{ barrios : contiene
  municipios ||--o{ areas_municipales : organiza
  areas_municipales }o--o{ barrios : "area_barrio"
  areas_municipales }o--o{ categorias : "area_categoria"
  areas_municipales |o--o{ usuarios : "agentes del área"
  usuarios ||--o{ reclamos : "ciudadano_id"
  usuarios |o--o{ reclamos : "agente_id"
  categorias ||--o{ reclamos : "categoria_id"
  barrios ||--o{ reclamos : "barrio_id"
  areas_municipales |o--o{ reclamos : "area_id"
  reclamos ||--|{ historial_estados : registra
  usuarios |o--o{ historial_estados : "usuario_id"
  reclamos ||--o{ notificaciones : genera
  usuarios ||--o{ notificaciones : "destinatario_id"

  usuarios {
    bigint id PK
    string tipo "CIUDADANO, AGENTE_MUNICIPAL o ADMINISTRADOR"
    string dni UK
    string nombre
    string apellido
    string email
    string telefono
    boolean activo
    bigint area_id FK "solo agentes"
  }
  municipios {
    bigint id PK
    string nombre
    string provincia
  }
  barrios {
    bigint id PK
    string nombre
    bigint municipio_id FK
  }
  categorias {
    bigint id PK
    string nombre
    string descripcion
    int sla_horas
    string prioridad_base
  }
  areas_municipales {
    bigint id PK
    string nombre
    string email_contacto
    boolean activa
    bigint municipio_id FK
  }
  reclamos {
    bigint id PK
    string numero UK
    string descripcion
    string direccion
    string estado
    string prioridad
    boolean vencido
    datetime fecha_creacion
    datetime fecha_limite
    bigint ciudadano_id FK
    bigint agente_id FK
    bigint categoria_id FK
    bigint barrio_id FK
    bigint area_id FK
  }
  historial_estados {
    bigint id PK
    string estado_anterior
    string estado_nuevo
    datetime fecha
    string observacion
    bigint reclamo_id FK
    bigint usuario_id FK "nulo si lo hizo el sistema"
  }
  notificaciones {
    bigint id PK
    string canal "siempre INTERNO en el Hito 1"
    string mensaje
    datetime fecha_envio
    bigint reclamo_id FK
    bigint destinatario_id FK
  }
```
