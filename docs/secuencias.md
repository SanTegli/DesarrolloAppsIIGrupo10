# Proceso de negocio y secuencias

## Ciclo de vida del reclamo

Las transiciones y los roles están definidos en un único lugar: `PoliticaTransiciones`.

```mermaid
stateDiagram-v2
  [*] --> INGRESADO : ciudadano crea
  INGRESADO --> ASIGNADO : sistema o administrador asigna área
  INGRESADO --> RECHAZADO : administrador rechaza
  INGRESADO --> CANCELADO : ciudadano cancela
  ASIGNADO --> ASIGNADO : administrador reasigna
  ASIGNADO --> EN_PROCESO : agente del área toma
  ASIGNADO --> CANCELADO : ciudadano cancela
  EN_PROCESO --> RESUELTO : agente resuelve
  RESUELTO --> CERRADO : ciudadano confirma
  RESUELTO --> EN_PROCESO : ciudadano reabre
  RECHAZADO --> [*]
  CANCELADO --> [*]
  CERRADO --> [*]
```

Una transición que no está en el diagrama responde `409`. Una que está, pedida por un rol que no
corresponde o sobre un reclamo ajeno, responde `403`.

## Proceso de negocio

Diagrama simplificado. Cada paso indica quién lo ejecuta.

```mermaid
flowchart TB
  A(["Ciudadano: detecta un problema"]) --> B["Ciudadano: crea el reclamo"]
  B --> C["Sistema: calcula la prioridad"]
  C --> D{"¿Hay un área que atienda<br/>la categoría en el barrio?"}
  D -- Sí --> E["Sistema: asigna el área"]
  D -- No --> F["Sistema: lo deja ingresado, sin área"]
  F --> G{"Administrador:<br/>¿corresponde al municipio?"}
  G -- No --> H(["Administrador: rechaza el reclamo"])
  G -- Sí --> I["Administrador: asigna un área a mano"]
  E --> J["Agente del área: toma el reclamo"]
  I --> J
  J --> K["Agente del área: trabaja y lo marca resuelto"]
  K --> L{"Ciudadano:<br/>¿quedó resuelto?"}
  L -- Sí --> M(["Ciudadano: confirma y el reclamo se cierra"])
  L -- No --> N["Ciudadano: reabre el reclamo"]
  N --> K
```

El ciudadano recibe un aviso en cada paso. Puede cancelar el reclamo mientras nadie lo haya tomado.

## Secuencia: creación de un reclamo

```mermaid
sequenceDiagram
  autonumber
  actor U as Ciudadano
  participant UI as React
  participant C as ReclamoController
  participant F as GestionReclamosFacade
  participant S as ReclamoService
  participant FB as ReclamoFactory
  participant EP as EstrategiaPrioridad
  participant EA as EstrategiaAsignacion
  participant R as Repositories
  participant P as PublicadorEventos
  participant L as NotificacionesReclamoListener

  U->>UI: completa el formulario
  UI->>C: POST /api/reclamos con X-Usuario-Id
  C->>F: crear(usuarioId, categoriaId, barrioId, descripcion, direccion)
  F->>S: crear(...)
  Note over S: comienza la transacción
  S->>R: buscar usuario, categoría y barrio
  R-->>S: ciudadano, categoría, barrio
  S->>FB: crear(ciudadano, categoria, barrio, descripcion, direccion)
  FB-->>S: reclamo INGRESADO con número, fecha límite e historial
  S->>EP: calcular(reclamo)
  EP-->>S: prioridad
  S->>R: buscarCandidatas(categoriaId, barrioId)
  R-->>S: áreas candidatas
  S->>EA: seleccionarArea(reclamo, candidatas)
  alt hay un área candidata
    EA-->>S: área
    S->>S: reclamo.asignarArea(area): pasa a ASIGNADO
  else ninguna área lo cubre
    EA-->>S: vacío: el reclamo sigue INGRESADO
  end
  S->>R: guardar(reclamo)
  S->>P: publicar(ReclamoCreado)
  P->>L: creado(evento)
  L->>R: guardar(Notificacion al ciudadano)
  opt el reclamo quedó asignado
    S->>P: publicar(ReclamoAsignado)
    P->>L: asignado(evento)
    L->>R: guardar(Notificacion al ciudadano)
  end
  Note over S: confirma la transacción
  S-->>F: reclamo
  F-->>C: reclamo
  C->>F: accionesDisponibles(usuarioId, numero)
  F-->>C: acciones del usuario
  C-->>UI: 201 Created, Location y ReclamoResponse
  UI-->>U: detalle del reclamo con sus acciones
```

Intervienen los cinco patrones: Facade (paso 3), Factory (7), Strategy (9 y 13), Repository (5, 11,
17) y Observer (18 a 23).

## Secuencia: cambio de estado

El ejemplo es un agente que toma un reclamo. Las demás transiciones recorren el mismo camino.

```mermaid
sequenceDiagram
  autonumber
  actor A as Agente municipal
  participant UI as React
  participant C as ReclamoController
  participant F as GestionReclamosFacade
  participant S as ReclamoService
  participant RC as Reclamo
  participant PT as PoliticaTransiciones
  participant R as Repositories
  participant P as PublicadorEventos
  participant L as NotificacionesReclamoListener
  participant M as ManejadorGlobalErrores

  A->>UI: pulsa "Tomar reclamo"
  UI->>C: PATCH /api/reclamos/{numero}/estado con X-Usuario-Id
  C->>F: cambiarEstado(usuarioId, numero, EN_PROCESO, observacion)
  F->>S: cambiarEstado(...)
  S->>R: buscar usuario y reclamo
  R-->>S: agente, reclamo ASIGNADO
  S->>RC: cambiarEstado(EN_PROCESO, agente, observacion, ahora)
  RC->>PT: validar(ASIGNADO, EN_PROCESO, AGENTE_MUNICIPAL)
  alt la transición no existe
    PT-->>M: ReglaNegocioException
    M-->>UI: 409 REGLA_NEGOCIO
  else el rol no puede, o el reclamo es de otra área
    RC-->>M: AccesoDenegadoException
    M-->>UI: 403 ACCESO_DENEGADO
  else permitido
    RC->>RC: guarda el agente, cambia el estado y agrega el historial
    S->>R: guardar(reclamo)
    S->>P: publicar(EstadoReclamoCambiado)
    P->>L: estadoCambiado(evento)
    L->>R: guardar(Notificacion al ciudadano)
    S-->>F: reclamo
    F-->>C: reclamo
    C-->>UI: 200 OK y ReclamoResponse con nuevas acciones
    UI-->>A: estado "En proceso" y botón "Marcar como resuelto"
  end
```

Cuando el destino es `RESUELTO`, el servicio publica además `ReclamoResuelto`, y el aviso al
ciudadano sale de ese evento.

La asignación manual usa `PATCH /api/reclamos/{numero}/asignacion`: recorre el mismo camino, llama a
`Reclamo.asignarArea` y publica `ReclamoAsignado`.
