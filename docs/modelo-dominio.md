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
    -String hashContrasena
    -boolean bloqueado
    +getRol() Rol
    +getNombreCompleto() String
    +definirContrasena(String)
    +tieneContrasena() boolean
    +bloquear()
    +desbloquear()
  }
  class Ciudadano {
    -String telefonoVerificado
    -LocalDateTime fechaVerificacionTelefono
    +verificarTelefono(String, LocalDateTime)
    +tieneTelefonoVerificado() boolean
  }
  class Apoyo {
    -LocalDateTime fecha
  }
  class Distancia {
    <<utilidad>>
    +metros(double, double, double, double)$ double
  }
  class CodigoVerificacion {
    -String codigo
    -LocalDateTime fechaCreacion
    -LocalDateTime vence
    -LocalDateTime fechaUso
    +estaVigente(LocalDateTime) boolean
    +usar(LocalDateTime)
  }
  class PoliticaReapertura {
    <<value object>>
    -int maximo
    -Duration plazo
  }
  class Telefono {
    <<utilidad>>
    +normalizar(String)$ String
    +enmascarar(String)$ String
  }
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
    -OrigenPrioridad origenPrioridad
    -String motivoPrioridad
    -boolean vencido
    -boolean asignacionPendiente
    -String foto
    -LocalDateTime fechaCreacion
    -LocalDateTime fechaLimite
    -long version
    +asignarArea(AreaMunicipal, Usuario, String, LocalDateTime)
    +cambiarEstado(EstadoReclamo, Usuario, String, LocalDateTime, PoliticaReapertura)
    +getCantidadReaperturas() int
    +puedeReabrir(LocalDateTime, PoliticaReapertura) boolean
    +puedeSerDuplicado() boolean
    +marcarVencido(LocalDateTime) boolean
    +definirPrioridad(Prioridad, OrigenPrioridad, String)
    +registrarUbicacion(Ubicacion)
    +registrarFoto(String)
    +marcarAsignacionPendiente()
    +perteneceA(Usuario) boolean
  }
  class Ubicacion {
    <<value object>>
    -EstadoUbicacion estado
    -Double latitud
    -Double longitud
    -String direccionNormalizada
    -String proveedor
    +pendiente()$ Ubicacion
    +confirmada(double, double, String, String)$ Ubicacion
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
  class MensajeSaliente {
    -String eventId
    -String claveRuteo
    -String contenido
    -EstadoMensaje estado
    -int intentos
    +marcarPublicado(LocalDateTime)
    +registrarFalla(String)
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
  CodigoVerificacion "*" --> "1" Ciudadano
  Apoyo "*" --> "1" Ciudadano
  Apoyo "*" --> "1" Reclamo
  Reclamo ..> PoliticaReapertura
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
  Reclamo *-- "1" Ubicacion
  HistorialEstado "*" --> "0..1" Usuario
  Notificacion "*" --> "1" Reclamo
  Notificacion "*" --> "1" Usuario : destinatario
  Reclamo ..> PoliticaTransiciones : valida con
  ReclamoFactory ..> Reclamo : crea
```

`Municipio`, `Barrio`, `Categoria`, `HistorialEstado`, `Notificacion` y `MensajeSaliente` también heredan
de `Entidad`; no se dibuja para no cargar el diagrama. `Ubicacion` no es una entidad: es un valor que se
guarda en columnas de la tabla `reclamos`. `MensajeSaliente` no tiene reglas de negocio: es la fila de la
outbox (tabla `mensajes_salientes`) con un aviso que tiene que salir por RabbitMQ.

| Enum | Valores |
| --- | --- |
| `EstadoReclamo` | `INGRESADO`, `ASIGNADO`, `EN_PROCESO`, `RESUELTO`, `CERRADO`, `RECHAZADO`, `CANCELADO` |
| `Prioridad` | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `Rol` | `CIUDADANO`, `AGENTE_MUNICIPAL`, `ADMINISTRADOR`, `SISTEMA` |
| `CanalNotificacion` | `EMAIL`, `SMS`, `INTERNO` |
| `OrigenPrioridad` | `CATEGORIA`, `PALABRAS_CLAVE`, `IA`, `RESPALDO` |
| `EstadoUbicacion` | `CONFIRMADA`, `PENDIENTE` |
| `EstadoMensaje` | `PENDIENTE`, `PUBLICADO` |

`Rol.SISTEMA` no corresponde a ningún usuario: representa las acciones automáticas, como la
asignación al crear el reclamo. Todos los avisos se guardan con el canal `INTERNO`; con la mensajería
habilitada, además salen por correo a través de la outbox.

## Reglas que viven en el dominio

| Regla | Dónde está |
| --- | --- |
| Qué transiciones de estado existen y qué rol puede ejecutar cada una | `PoliticaTransiciones` |
| Un ciudadano solo opera sobre sus reclamos; un agente, sobre los de su área | `Reclamo.cambiarEstado` |
| Solo se asigna a un área activa y distinta de la actual | `Reclamo.asignarArea` |
| Un área puede atender si está activa, atiende la categoría y cubre el barrio | `AreaMunicipal.puedeAtender` |
| Un reclamo vence una sola vez y sube un nivel de prioridad | `Reclamo.marcarVencido` |
| Solo un reclamo `INGRESADO` queda con la asignación pendiente; asignarle un área la borra | `Reclamo.marcarAsignacionPendiente` y `asignarArea` |
| Una ubicación confirmada tiene coordenadas válidas, dirección y proveedor; nunca se inventan | `Ubicacion.confirmada` |
| Una ubicación solo se confirma si el ciudadano eligió una sugerencia del geocodificador dentro de su municipio | `ServicioUbicaciones.confirmar` |
| Todo reclamo nace `INGRESADO`, con número, fecha límite e historial | `ReclamoFactory.crear` |
| Un usuario guarda el hash de su contraseña, nunca la contraseña; un hash vacío no se acepta | `Usuario.definirContrasena` |
| Reabrir exige motivo, se permite hasta `maximo` veces y dentro de `plazo` desde la última resolución; ambos datos salen del historial | `Reclamo.cambiarEstado` con `PoliticaReapertura` |
| Un teléfono se guarda en E.164, de 8 a 15 dígitos, y se muestra enmascarado | `Telefono.normalizar` y `enmascarar` |
| Un código de verificación se usa una sola vez y antes de vencer | `CodigoVerificacion.usar` |
| Un reclamo guarda la referencia a su foto una sola vez; no se reemplaza | `Reclamo.registrarFoto` |
| Solo un reclamo abierto y con la ubicación confirmada aparece como posible duplicado | `Reclamo.puedeSerDuplicado` |
| Nadie apoya su propio reclamo ni uno que ya no está abierto; apoyar no cambia la prioridad | Constructor de `Apoyo` |

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
  usuarios ||--o{ codigos_verificacion : "ciudadano_id"
  usuarios ||--o{ apoyos : "ciudadano_id"
  reclamos ||--o{ apoyos : "reclamo_id"

  usuarios {
    bigint id PK
    string tipo "CIUDADANO, AGENTE_MUNICIPAL o ADMINISTRADOR"
    string dni UK
    string nombre
    string apellido
    string email
    string telefono
    boolean activo
    string hash_contrasena "BCrypt; nulo si no puede entrar"
    boolean bloqueado "default false"
    string telefono_verificado UK "E.164; solo ciudadanos"
    datetime fecha_verificacion_telefono
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
    string origen_prioridad "nulo en reclamos anteriores a la Segunda Parte"
    string motivo_prioridad
    boolean vencido
    boolean asignacion_pendiente
    string foto "referencia en el almacén; nula antes del PR 16"
    datetime fecha_creacion
    datetime fecha_limite
    bigint version "bloqueo optimista"
    string ubicacion_estado
    double ubicacion_latitud
    double ubicacion_longitud
    string ubicacion_direccion
    string ubicacion_proveedor
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
    string canal "siempre INTERNO"
    string mensaje
    datetime fecha_envio
    bigint reclamo_id FK
    bigint destinatario_id FK
  }
  apoyos {
    bigint id PK
    datetime fecha
    bigint ciudadano_id FK "único junto con reclamo_id"
    bigint reclamo_id FK
  }
  codigos_verificacion {
    bigint id PK
    string codigo UK "8 caracteres"
    datetime fecha_creacion
    datetime vence
    datetime fecha_uso "nulo hasta que se usa"
    bigint ciudadano_id FK
  }
  mensajes_salientes {
    bigint id PK
    string event_id UK
    string clave_ruteo
    string tipo_evento
    string contenido "JSON del mensaje"
    string estado "PENDIENTE o PUBLICADO"
    int intentos
    datetime fecha_creacion
    datetime fecha_publicacion
    string ultimo_error
  }
```

`mensajes_salientes` es la outbox: no tiene claves foráneas, porque el mensaje ya lleva todo lo que necesita
el consumidor y tiene que poder publicarse aunque cambien los datos del reclamo.

`telefono_verificado` es único: es lo que asegura "un número, una cuenta" aun si dos confirmaciones llegan a la
vez. El campo `telefono` es el que se cargó en el alta, sin verificar; el que cuenta para crear reclamos es
`telefono_verificado`.
