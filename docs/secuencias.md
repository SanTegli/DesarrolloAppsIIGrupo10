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

`RESUELTO → EN_PROCESO` (reabrir) tiene una regla más, en `PoliticaReapertura`: se permite **una vez**,
con un motivo, y **dentro de los 7 días** de la resolución. Después la acción ya no se ofrece; si el problema
sigue, el ciudadano puede crear un reclamo nuevo.

El vencimiento no es un estado. Un reclamo `INGRESADO`, `ASIGNADO` o `EN_PROCESO` que pasa su fecha
límite queda marcado como vencido y sube un nivel de prioridad, sin cambiar de estado.

## Proceso de negocio

Diagrama simplificado (BPMN simplificado como diagrama de flujo). Cada paso indica quién lo ejecuta.

```mermaid
flowchart TB
  A(["Ciudadano: detecta un problema"]) --> V{"¿Tiene el teléfono<br/>verificado?"}
  V -- No --> VT["Ciudadano: verifica el teléfono<br/>con el bot de Telegram"]
  VT --> B
  V -- Sí --> B["Ciudadano: completa el reclamo,<br/>con foto y ubicación"]
  B --> DU{"¿Hay un reclamo abierto igual<br/>a menos de 50 m?"}
  DU -- "Sí, es el mismo" --> AP(["Ciudadano: lo apoya y no crea otro"])
  DU -- "No, o es otro problema" --> CU{"Sistema: ¿cuenta habilitada<br/>y dentro del límite diario?"}
  CU -- No --> NO(["Sistema: rechaza el alta (403 o 429)"])
  CU -- Sí --> C["Sistema: calcula la prioridad con la IA<br/>(nunca por debajo de la categoría)"]
  C --> D{"¿Hay un área que atienda<br/>la categoría en el barrio?"}
  D -- Sí --> E["Sistema: asigna el área"]
  D -- No --> F["Sistema: lo deja ingresado, sin área"]
  F --> G{"Administrador:<br/>¿corresponde al municipio?"}
  G -- No --> H(["Administrador: rechaza el reclamo"])
  G -- Sí --> I["Administrador: asigna un área a mano"]
  E --> J["Agente del área: toma el reclamo"]
  I --> J
  E -.-> X(["Ciudadano: cancela el reclamo<br/>(solo antes de que lo tomen)"])
  F -.-> X
  J --> K["Agente del área: trabaja y lo marca resuelto"]
  K --> L{"Ciudadano:<br/>¿quedó resuelto?"}
  L -- Sí --> M(["Ciudadano: confirma y el reclamo se cierra"])
  L -- No --> R{"¿Ya lo reabrió, o pasaron<br/>más de 7 días?"}
  R -- No --> N["Ciudadano: reabre con un motivo"]
  N --> K
  R -- Sí --> Z(["Ciudadano: crea un reclamo nuevo"])
```

- **Avisos:** el ciudadano recibe un aviso en cada paso, en la aplicación y por correo, y el área, cuando le
  asignan un reclamo.
- **Vencimiento:** no es un paso. Un reclamo pendiente que pasa su fecha límite queda marcado como vencido,
  sube un nivel de prioridad y se avisa, sin cambiar de estado.

## Secuencia: creación de un reclamo

```mermaid
sequenceDiagram
  autonumber
  actor U as Ciudadano
  participant UI as React
  participant C as ReclamoController
  participant F as GestionReclamosFacade
  participant D as ServicioDuplicados
  participant S as ReclamoService
  participant CU as PermisosReclamo y ControlesDeUso
  participant FB as ReclamoFactory
  participant EP as EstrategiaPrioridad
  participant SA as ServicioAsignacion
  participant EA as EstrategiaAsignacion
  participant R as Repositories
  participant P as PublicadorEventos
  participant L as Listeners

  U->>UI: completa el formulario y elige la ubicación en el mapa
  UI->>C: GET /api/reclamos/candidatos-duplicados (categoría, latitud, longitud)
  C->>F: candidatosDuplicados(usuarioId, categoriaId, latitud, longitud)
  F->>D: candidatos(...)
  D->>R: reclamos abiertos de la categoría en el recuadro
  D-->>C: los que están a menos de 50 m, sin datos del autor
  C-->>UI: 200 con los candidatos (puede ser una lista vacía)
  alt el ciudadano reconoce su problema
    UI->>C: POST /api/reclamos/{numero}/apoyo
    Note over UI: no se crea un reclamo nuevo (ver Duplicados y apoyos en arquitectura)
  else es otro problema, o no hay candidatos
    UI->>C: POST /api/reclamos (multipart: reclamo y foto) con la cookie de sesión y X-XSRF-TOKEN
    C->>F: crear(usuarioId, categoriaId, barrioId, descripcion, direccion, ubicación, foto)
    Note over F: valida y re-codifica la foto y la guarda como pendiente (ver arquitectura, Foto del reclamo)
    F->>S: crear(..., referencia de la foto)
    Note over S: comienza la transacción
    S->>R: buscar usuario
    R-->>S: usuario
    S->>CU: validarCreacion(usuario) y antesDeCrear(ciudadano)
    Note over CU: es ciudadano, teléfono verificado, cuenta no bloqueada y menos del límite diario.<br/>Si no: 403 o 429, y la foto pendiente se descarta
    S->>R: buscar categoría y barrio
    R-->>S: categoría, barrio
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
  end
```

Intervienen los cinco patrones: Facade (pasos 3 y 10), Factory (17), Strategy (19 y 24), Repository (5,
12, 15 y 28) y Observer (29 a 35). Antes del alta, los controles de uso (paso 14) son una Chain of
Responsibility: ver [patrones](patrones.md#chain-of-responsibility-propia-controles-de-uso).

## Secuencia: el alta y sus cinco integraciones

La secuencia anterior muestra las clases del backend. Esta muestra los procesos, tal como corre en la demo
(`docker compose up`): el mismo reclamo pasa por Georef, la IA, el servicio SOAP y RabbitMQ hasta llegar como
correo. La sexta integración, Telegram, no está en el alta: se usa antes, una sola vez, para verificar el
teléfono (ver [Teléfono verificado con Telegram](arquitectura.md#teléfono-verificado-con-telegram-segunda-api-externa)). Cada llamada externa tiene su respaldo; el detalle está en la [matriz de fallos](demo.md#matriz-de-fallos).

```mermaid
sequenceDiagram
  autonumber
  actor U as Ciudadano
  participant UI as React
  participant B as Backend (REST 1)
  participant GEO as Georef
  participant PRI as servicio-prioridad
  participant LLM as Gemini
  participant COB as servicio-cobertura
  participant DB as MySQL
  participant MQ as RabbitMQ
  participant NOT as servicio-notificaciones
  participant MAIL as Mailpit

  U->>UI: escribe la dirección y toca "Buscar en el mapa"
  UI->>B: GET /api/ubicaciones/sugerencias
  B->>GEO: GET /direcciones (tiempo límite 3 s)
  GEO-->>B: direcciones con coordenadas
  B-->>UI: sugerencias dentro del municipio
  U->>UI: elige una sugerencia
  UI->>B: GET /api/reclamos/candidatos-duplicados
  B->>DB: reclamos abiertos de la misma categoría cerca del punto
  B-->>UI: posibles duplicados, sin datos del autor
  Note over U,UI: si reconoce su problema, lo apoya y no crea otro
  U->>UI: "Es otro problema": crea el reclamo
  UI->>B: POST /api/reclamos (multipart) con la ubicación elegida y la foto
  B->>B: valida la foto (JPEG o PNG), la re-codifica sin EXIF y la guarda como pendiente
  B->>B: controles de uso: teléfono verificado, cuenta no bloqueada, límite diario
  B->>B: valida que la ubicación caiga en el municipio
  Note over B,DB: comienza la transacción
  B->>PRI: POST /api/prioridades/evaluar (categoría y descripción)
  PRI->>LLM: generateContent con las instrucciones
  LLM-->>PRI: JSON con prioridad y motivo
  PRI-->>B: prioridad sugerida
  Note right of B: si falla o pasan 4 s: prioridad de la categoría (PENDIENTE_IA)<br/>y pedido de revisión por la cola (PR 24)
  B->>COB: ConsultarCobertura (municipio, barrio, categoría)
  COB-->>B: áreas que lo cubren
  Note right of B: si falla o pasan 2 s: asignación pendiente
  B->>DB: guarda el reclamo, sus avisos y una fila PENDIENTE por aviso (y por la revisión de la IA) en mensajes_salientes
  Note over B,DB: confirma la transacción
  B->>B: confirma la foto (la mueve del directorio de pendientes)
  B-->>UI: 201 Created con el reclamo
  loop cada 2 segundos
    B->>DB: lee los mensajes PENDIENTES
    B->>MQ: publica en reclamos.eventos (aviso.*)
    MQ-->>B: confirmación
    B->>DB: los marca PUBLICADOS
  end
  MQ->>NOT: entrega desde notificaciones.email
  NOT->>NOT: ¿eventId ya procesado? descarta
  NOT->>MAIL: SMTP: "Reclamo REC-…: lo recibimos"
  NOT->>NOT: registra el eventId
  NOT-->>MQ: ack
```

Georef se consulta antes del alta, cuando el ciudadano busca; el alta solo valida la sugerencia que eligió. La
IA y el SOAP corren dentro de la transacción: en el peor caso el alta espera unos 8 segundos (6 de la IA y 2 del
SOAP). La publicación en RabbitMQ y el correo pasan después de responder: el ciudadano no los espera.

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
  UI->>C: PATCH /api/reclamos/{numero}/estado con la cookie de sesión y X-XSRF-TOKEN
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
