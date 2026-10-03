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

El vencimiento no es un estado. Un reclamo `INGRESADO`, `ASIGNADO` o `EN_PROCESO` que pasa su fecha
límite queda marcado como vencido y sube un nivel de prioridad, sin cambiar de estado.

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
  participant SA as ServicioAsignacion
  participant EA as EstrategiaAsignacion
  participant R as Repositories
  participant P as PublicadorEventos
  participant L as Listeners

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
  S->>SA: asignarAutomaticamente(reclamo, categoriaId, barrioId)
  SA->>R: buscarCandidatas(categoriaId, barrioId)
  R-->>SA: áreas candidatas
  SA->>EA: seleccionarArea(reclamo, candidatas)
  alt hay un área candidata
    EA-->>SA: área
    SA->>SA: reclamo.asignarArea(area): pasa a ASIGNADO
  else ninguna área lo cubre
    EA-->>SA: vacío: el reclamo sigue INGRESADO
  end
  S->>R: guardar(reclamo)
  S->>P: publicar(ReclamoCreado)
  P->>L: NotificacionesReclamoListener.creado(evento)
  L->>R: guardar(aviso al ciudadano)
  opt el reclamo quedó asignado
    S->>P: publicar(ReclamoAsignado)
    P->>L: NotificacionesReclamoListener.asignado(evento)
    L->>R: guardar(avisos al ciudadano y a los agentes del área)
  end
  Note over S: confirma la transacción
  P->>L: AuditoriaListener.registrar(cada evento)
  S-->>F: reclamo
  F-->>C: reclamo
  C->>F: accionesDisponibles(usuarioId, numero)
  F-->>C: acciones del usuario
  C-->>UI: 201 Created, Location y ReclamoResponse
  UI-->>U: detalle del reclamo con sus acciones
```

Intervienen los cinco patrones: Facade (paso 3), Factory (7), Strategy (9 y 14), Repository (5, 12,
18) y Observer (19 a 25).

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
  participant L as Listeners
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
    P->>L: NotificacionesReclamoListener.estadoCambiado(evento)
    L->>R: guardar(aviso al ciudadano)
    P->>L: AuditoriaListener.registrar(evento), después del commit
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

## Secuencia: vencimiento de un reclamo

Es el único proceso que no arranca con una acción de un usuario: lo dispara una tarea programada.

```mermaid
sequenceDiagram
  autonumber
  participant T as TareaVencimientos
  participant V as ServicioVencimientos
  participant R as Repositories
  participant RC as Reclamo
  participant P as PublicadorEventos
  participant N as NotificacionesReclamoListener
  participant A as AuditoriaListener

  loop cada minuto
    T->>V: marcarVencidos()
    Note over V: comienza la transacción
    V->>R: buscarVencidosSinMarcar(ahora)
    R-->>V: reclamos pendientes con fecha límite pasada
    loop por cada reclamo
      V->>RC: marcarVencido(ahora)
      RC-->>V: true: quedó vencido y subió la prioridad
      V->>R: guardar(reclamo)
      V->>P: publicar(ReclamoVencido)
      P->>N: vencido(evento)
      N->>R: guardar(avisos al ciudadano y al agente o al área)
    end
    Note over V: confirma la transacción
    P->>A: registrar(cada evento)
    V-->>T: cantidad de reclamos marcados
  end
```

Cada reclamo se marca una sola vez: en la siguiente ejecución ya no aparece entre los vencidos sin
marcar, así que no se repiten eventos ni avisos.
