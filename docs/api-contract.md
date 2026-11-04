# Contrato REST

Contrato entre el backend y el frontend. Lo implementan los controladores de `reclamos-app` y lo
consume `frontend/src/api/`. La API simulada del frontend (`npm run mock`) sigue este mismo documento.

El mismo contrato en OpenAPI, generado del código, está en [`openapi/reclamos.yaml`](openapi/README.md)
y en Swagger UI (`http://localhost:8080/swagger-ui.html`). Este documento explica las reglas que el
OpenAPI no cuenta: permisos por rol, transiciones y datos semilla.

Si alguien necesita cambiar algo de acá, lo avisa al grupo antes: es el único archivo del que
dependen tres personas a la vez.

## Convenciones

| Tema | Regla |
| --- | --- |
| URL base | `http://localhost:8080/api` |
| Formato | JSON en UTF-8, `Content-Type: application/json` |
| Fechas | ISO-8601 sin zona horaria: `2026-10-05T10:00:00`. Las del historial y los avisos pueden traer fracción de segundo (`2026-10-05T10:00:00.123456`) |
| Identidad | Sesión del servidor: `POST /api/auth/login` deja la cookie `JSESSIONID` (`HttpOnly`) y el navegador la manda sola. Ver [Sesión](#sesión) |
| CSRF | Cada `POST` y `PATCH` lleva el encabezado `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`, que el backend deja en cualquier respuesta |
| Número de reclamo | `REC-` más ocho caracteres hexadecimales en mayúscula: `REC-4F2A91BC` |

Todo `/api` exige sesión, salvo el login, el registro y los catálogos públicos (`/api/categorias`,
`/api/barrios` y `/api/areas`), que la interfaz necesita antes de que alguien entre. Sin sesión, la respuesta
es `401 NO_AUTENTICADO`. Qué puede hacer cada usuario lo decide su rol, guardado en la base: la sesión solo
dice quién es.

### Enumerados

| Enumerado | Valores |
| --- | --- |
| `rol` | `CIUDADANO`, `AGENTE_MUNICIPAL`, `ADMINISTRADOR` |
| `estado` | `INGRESADO`, `ASIGNADO`, `EN_PROCESO`, `RESUELTO`, `CERRADO`, `RECHAZADO`, `CANCELADO` |
| `prioridad` | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `origenPrioridad` | `CATEGORIA`, `PALABRAS_CLAVE`, `IA`, `RESPALDO` |
| `ubicacion.estado` | `CONFIRMADA`, `PENDIENTE` |

### Errores

Todos los errores tienen la misma forma:

```json
{
  "status": 409,
  "codigo": "REGLA_NEGOCIO",
  "mensaje": "Un reclamo en estado INGRESADO no puede pasar a RESUELTO.",
  "detalles": [],
  "ruta": "/api/reclamos/REC-4F2A91BC/estado",
  "fecha": "2026-10-05T10:00:00"
}
```

`detalles` solo trae contenido en los errores de validación: un texto por campo inválido.

| HTTP | `codigo` | Cuándo | Excepción del backend |
| --- | --- | --- | --- |
| 400 | `DATOS_INVALIDOS` | Falta un campo o tiene formato incorrecto | `DatosInvalidosException` |
| 401 | `NO_AUTENTICADO` | No hay sesión, o venció | `RespuestasDeSeguridad` (Spring Security) |
| 401 | `CREDENCIALES_INVALIDAS` | El login falló. Mismo mensaje si el email no existe, la contraseña está mal o la cuenta está desactivada | `CredencialesInvalidasException` |
| 403 | `ACCESO_DENEGADO` | El usuario existe pero su rol no puede hacer la operación, o el reclamo no es suyo ni de su área | `AccesoDenegadoException` |
| 403 | `TELEFONO_NO_VERIFICADO` | Un ciudadano sin teléfono verificado intenta crear o reabrir un reclamo | `UsoRestringidoException` |
| 403 | `CUENTA_BLOQUEADA` | Un ciudadano con la cuenta bloqueada intenta crear, reabrir o verificar el teléfono | `UsoRestringidoException` |
| 403 | `CSRF_INVALIDO` | Un `POST` o `PATCH` sin el encabezado `X-XSRF-TOKEN`, o con un valor distinto al de la cookie | `RespuestasDeSeguridad` (Spring Security) |
| 404 | `RECURSO_NO_ENCONTRADO` | No existe el usuario, reclamo, barrio, categoría o área | `RecursoNoEncontradoException` |
| 409 | `REGLA_NEGOCIO` | Transición de estado inexistente u otra regla del negocio | `ReglaNegocioException` |
| 409 | `CONFLICTO_CONCURRENTE` | Otra operación modificó el mismo reclamo mientras se procesaba esta; hay que volver a cargarlo | Bloqueo optimista de JPA |
| 413 | `ARCHIVO_DEMASIADO_GRANDE` | La foto pesa más de 5 MB o mide más de 4000 píxeles de lado | `ArchivoDemasiadoGrandeException` o el límite de Spring |
| 429 | `LIMITE_EXCEDIDO` | Se superó un límite de uso: reclamos por día o códigos de verificación por hora | `LimiteExcedidoException` |
| 405 | `METODO_NO_PERMITIDO` | El recurso no admite ese método HTTP | Error de Spring |
| 415 | `TIPO_CONTENIDO_NO_SOPORTADO` | El cuerpo no es `application/json` | Error de Spring |
| 500 | `ERROR_INTERNO` | Error inesperado | Cualquier otra |

Cuando una transición no existe y además el rol no alcanza, gana el 409.

## Objetos

### Reclamo

Lo devuelven la creación, la consulta por número, el cambio de estado y la asignación.

```json
{
  "numero": "REC-4F2A91BC",
  "descripcion": "Farol apagado frente a la plaza",
  "direccion": "Belgrano 450",
  "ubicacion": {
    "estado": "PENDIENTE",
    "latitud": null,
    "longitud": null,
    "direccionNormalizada": null,
    "proveedor": null
  },
  "foto": "/api/reclamos/REC-4F2A91BC/foto",
  "cantidadApoyos": 0,
  "estado": "ASIGNADO",
  "prioridad": "MEDIA",
  "origenPrioridad": "CATEGORIA",
  "motivoPrioridad": null,
  "vencido": false,
  "asignacionPendiente": false,
  "fechaCreacion": "2026-10-05T10:00:00",
  "fechaLimite": "2026-10-07T10:00:00",
  "categoria": { "id": 1, "nombre": "Luminaria rota" },
  "barrio": { "id": 2, "nombre": "Bernal" },
  "ciudadano": { "id": 1, "nombreCompleto": "Ana Pérez" },
  "area": { "id": 1, "nombre": "Alumbrado" },
  "agente": null,
  "historial": [
    {
      "estadoAnterior": null,
      "estadoNuevo": "INGRESADO",
      "fecha": "2026-10-05T10:00:00",
      "observacion": "Reclamo ingresado",
      "usuario": { "id": 1, "nombreCompleto": "Ana Pérez" }
    },
    {
      "estadoAnterior": "INGRESADO",
      "estadoNuevo": "ASIGNADO",
      "fecha": "2026-10-05T10:00:00",
      "observacion": "Asignación automática",
      "usuario": null
    }
  ],
  "accionesDisponibles": ["CANCELADO"]
}
```

- `ubicacion.estado` es `CONFIRMADA` cuando el ciudadano eligió una sugerencia del geocodificador, con
  coordenadas, dirección normalizada y proveedor; si no, `PENDIENTE` y los demás campos en `null`.
- `foto` es la ruta de la foto del problema ([`GET /api/reclamos/{numero}/foto`](#get-apireclamosnumerofoto--foto-del-reclamo)).
  Es `null` en los reclamos creados antes de que la foto fuera obligatoria.
- `cantidadApoyos` es cuántos vecinos apoyan el reclamo ([apoyos](#apoyos-y-posibles-duplicados)). No cambia la
  prioridad.
- `origenPrioridad` dice qué decidió la prioridad: `CATEGORIA`, `PALABRAS_CLAVE`, `IA` o `RESPALDO` (la IA
  no respondió y se usó la de la categoría). `motivoPrioridad` la explica. Es `null` cuando la prioridad es
  la de la categoría sin otro análisis; con `reclamos.prioridad.estrategia: ia`, si la IA sugirió menos que
  la categoría, el origen queda `CATEGORIA` y el motivo dice qué sugirió la IA.
- `asignacionPendiente` es `true` cuando el servicio de cobertura no respondió a tiempo o rechazó la
  consulta al crear el reclamo: queda `INGRESADO` y un administrador lo asigna a mano. No es lo mismo que
  no tener área compatible. Solo puede pasar con `reclamos.cobertura.origen: soap`.
- `area` es `null` mientras el reclamo está `INGRESADO`.
- `agente` es `null` hasta que un agente toma el reclamo.
- `usuario` en el historial es `null` cuando el cambio lo hizo el sistema.
- `accionesDisponibles` son los estados a los que **el usuario del encabezado** puede llevar este
  reclamo. El frontend muestra un botón por cada uno y no repite las reglas. Si incluye `ASIGNADO`,
  el usuario puede asignar o reasignar el área con el endpoint de asignación.

### Resumen de reclamo

Lo devuelve el listado. Es el reclamo sin `historial` y sin `descripcion` completa:

```json
{
  "numero": "REC-4F2A91BC",
  "direccion": "Belgrano 450",
  "estado": "ASIGNADO",
  "prioridad": "MEDIA",
  "vencido": false,
  "asignacionPendiente": false,
  "fechaCreacion": "2026-10-05T10:00:00",
  "fechaLimite": "2026-10-07T10:00:00",
  "categoria": { "id": 1, "nombre": "Luminaria rota" },
  "barrio": { "id": 2, "nombre": "Bernal" },
  "ciudadano": { "id": 1, "nombreCompleto": "Ana Pérez" },
  "area": { "id": 1, "nombre": "Alumbrado" }
}
```

## Reclamos

### `POST /api/reclamos` — crear un reclamo

Solo `CIUDADANO`, con el **teléfono verificado** y la cuenta sin bloquear. El reclamo queda a nombre del
usuario de la sesión. Hasta `reclamos.uso.reclamos-por-dia` reclamos (5 por defecto) en las últimas 24 horas.

**Desde el PR 16 el alta es `multipart/form-data`, con la foto obligatoria.** Lleva dos partes:

| Parte | Tipo | Contenido |
| --- | --- | --- |
| `reclamo` | `application/json` | El JSON de abajo, el mismo de antes |
| `foto` | `image/jpeg` o `image/png` | La foto del problema: hasta 5 MB y 4000 × 4000 píxeles |

```bash
curl -b cookies.txt -H "X-XSRF-TOKEN: $TOKEN" \
  -F 'reclamo=<reclamo.json;type=application/json' \
  -F 'foto=@farol.jpg;type=image/jpeg' \
  http://localhost:8080/api/reclamos
```

El JSON de la parte `reclamo`:

```json
{
  "categoriaId": 1,
  "barrioId": 2,
  "descripcion": "Farol apagado frente a la plaza",
  "direccion": "Belgrano 450",
  "ubicacion": {
    "latitud": -34.73614578,
    "longitud": -58.3228998575,
    "direccionNormalizada": "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires"
  }
}
```

| Campo | Regla |
| --- | --- |
| `categoriaId` | Obligatorio; debe existir |
| `barrioId` | Obligatorio; debe existir |
| `descripcion` | Obligatoria, hasta 1000 caracteres |
| `direccion` | Obligatoria, hasta 200 caracteres. Es lo que escribió el ciudadano y se guarda siempre |
| `ubicacion` | Opcional: la sugerencia de [`GET /api/ubicaciones/sugerencias`](#get-apiubicacionessugerencias--buscar-la-dirección-en-el-mapa) que eligió el ciudadano, con sus tres campos. Las coordenadas tienen que caer dentro del recuadro del municipio del barrio. Sin `ubicacion`, el reclamo queda con la ubicación `PENDIENTE` |

Respuesta `201 Created` con el encabezado `Location: /api/reclamos/{numero}` y el objeto Reclamo.

**La foto:**

- El formato se reconoce por el contenido, no por el nombre ni por el tipo que declara el navegador: un
  `.txt` renombrado a `.jpg` responde `400`. GIF, WebP o HEIC también responden `400`.
- Se **re-codifica a JPEG**. Eso borra los metadatos (EXIF): la ubicación del GPS, el modelo del teléfono y
  la fecha no se guardan. Un PNG transparente queda sobre fondo blanco.
- Se guarda con un nombre aleatorio que no dice nada del reclamo ni del autor.
- No se gira según la orientación del EXIF ni se ocultan caras o patentes: ver los límites en
  [arquitectura](arquitectura.md#foto-del-reclamo).
- No viaja a la IA ni a RabbitMQ.

**Otros resultados del alta:**

- Si un área activa atiende la categoría y tiene jurisdicción sobre el barrio, el reclamo vuelve
  `ASIGNADO` con esa área.
- Si ninguna lo cubre, vuelve `INGRESADO` con `area: null`. Sigue siendo `201`.

Errores:

- `400`:
  - Datos inválidos o ubicación fuera del municipio (`La ubicación elegida queda fuera de Quilmes.`).
  - Sin foto (`El reclamo necesita una foto del problema.`).
  - Un archivo que no es JPEG ni PNG.
- `403 ACCESO_DENEGADO`: el usuario no es ciudadano.
- `403 TELEFONO_NO_VERIFICADO` y `403 CUENTA_BLOQUEADA`.
- `404`: usuario, barrio o categoría inexistente.
- `413 ARCHIVO_DEMASIADO_GRANDE`: la foto pesa más de 5 MB o mide más de 4000 píxeles de lado.
- `415`: el alta llega como JSON solo, sin multipart.
- `429 LIMITE_EXCEDIDO`: límite diario.

Los controles de uso se aplican en este orden, y responde el primero que falla: teléfono verificado, cuenta
sin bloquear, límite diario.

### `GET /api/reclamos/{numero}/foto` — foto del reclamo

La foto como `image/jpeg`, sin metadatos. **Mismos permisos que el detalle:** el ciudadano dueño, los
agentes del área asignada y los administradores. **Además, cualquier ciudadano** ve la foto de un reclamo
que puede aparecer como posible duplicado (abierto y ubicado en el mapa): es la foto de las tarjetas del
alta. Cuando el reclamo se resuelve, vuelve a ser solo del detalle. Se puede usar directo en un `<img>`, porque el navegador
manda la cookie de sesión. Responde con `Cache-Control: private, max-age=3600`.

Errores: `401` sin sesión; `403` el reclamo no es del usuario ni de su área; `404` el reclamo no existe o no
tiene foto (es anterior a la foto obligatoria).

## Apoyos y posibles duplicados

Antes de crear un reclamo con la ubicación marcada en el mapa, la interfaz busca reclamos abiertos de la
misma categoría a pocos metros. Si es el mismo problema, el vecino **lo apoya** en lugar de crear otro.

### `GET /api/reclamos/candidatos-duplicados` — posibles duplicados

| Parámetro | Regla |
| --- | --- |
| `categoriaId` | Obligatorio; debe existir |
| `latitud`, `longitud` | Obligatorios: el punto elegido en el mapa |
| `radio` | Opcional, en metros: por defecto 50, máximo 200 (`reclamos.duplicados.*`) |
| `limite` | Opcional: por defecto y como máximo 5 |

Devuelve reclamos `INGRESADO`, `ASIGNADO` o `EN_PROCESO` de la categoría, con la ubicación **confirmada**,
dentro del radio, del más cercano al más lejano. La distancia se calcula con la fórmula de Haversine. Lo
puede usar cualquier usuario con sesión.

```json
[
  {
    "numero": "REC-7C4046A4",
    "estado": "ASIGNADO",
    "fechaCreacion": "2026-11-03T17:34:00",
    "descripcion": "El farol frente a la plaza está apagado hace dos noches y el poste quedó inclinado.",
    "direccion": "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires",
    "foto": "/api/reclamos/REC-7C4046A4/foto",
    "distanciaMetros": 13,
    "cantidadApoyos": 0,
    "apoyadoPorMi": false,
    "propio": false
  }
]
```

**No lleva el nombre ni ningún dato del autor:** solo lo necesario para reconocer el problema. `propio` es
`true` si lo creó quien consulta, y en ese caso no se puede apoyar.

Errores: `400` latitud, longitud, radio o límite fuera de rango; `401` sin sesión; `404` categoría
inexistente.

### `POST` y `DELETE /api/reclamos/{numero}/apoyo` — apoyar o retirar el apoyo

Solo `CIUDADANO`. Los dos son **idempotentes**: apoyar dos veces deja un solo apoyo, y retirar uno que no
existe no cambia nada. Responden `200`:

```json
{ "numero": "REC-7C4046A4", "cantidadApoyos": 1, "apoyado": true }
```

| Regla | Respuesta |
| --- | --- |
| No es ciudadano | `403 ACCESO_DENEGADO` |
| Sin teléfono verificado o con la cuenta bloqueada (solo para apoyar) | `403 TELEFONO_NO_VERIFICADO` o `403 CUENTA_BLOQUEADA` |
| Es su propio reclamo | `409 REGLA_NEGOCIO` |
| El reclamo ya no está abierto (para apoyar; retirar se puede siempre) | `409 REGLA_NEGOCIO` |
| No existe el reclamo | `404` |

Apoyar **no da permisos** sobre el reclamo (no ve el detalle ni le cambia el estado) y **no cambia la
prioridad**. La base guarda un apoyo por ciudadano y reclamo (restricción única); si dos pedidos iguales
llegan a la vez, el segundo responde el estado actual.

### `GET /api/reclamos/{numero}` — consultar un reclamo

Respuesta `200 OK` con el objeto Reclamo.

| Rol | Qué puede consultar |
| --- | --- |
| `CIUDADANO` | Sus propios reclamos |
| `AGENTE_MUNICIPAL` | Los reclamos asignados a su área |
| `ADMINISTRADOR` | Cualquiera |

Errores: `403` el reclamo no es del usuario ni de su área, `404` reclamo o usuario inexistente.

### `GET /api/reclamos` — listar reclamos

El resultado depende del rol del usuario del encabezado, del más nuevo al más viejo:

| Rol | Qué devuelve |
| --- | --- |
| `CIUDADANO` | Sus reclamos |
| `AGENTE_MUNICIPAL` | Los reclamos asignados a su área |
| `ADMINISTRADOR` | Todos |

Parámetros opcionales:

| Parámetro | Quién | Efecto |
| --- | --- | --- |
| `estado` | Todos | Filtra por estado, por ejemplo `?estado=INGRESADO` |
| `areaId` | Solo administrador | Reclamos de un área |
| `ciudadanoId` | Solo administrador | Reclamos de un ciudadano |

Respuesta `200 OK` con un arreglo de resúmenes; `[]` si no hay ninguno.

### `PATCH /api/reclamos/{numero}/estado` — cambiar el estado

```json
{
  "estado": "EN_PROCESO",
  "observacion": "Cuadrilla en camino"
}
```

`observacion` es opcional. Respuesta `200 OK` con el objeto Reclamo actualizado.

| Desde | Hacia | Quién | Acción en la interfaz |
| --- | --- | --- | --- |
| `INGRESADO` | `RECHAZADO` | Administrador | Rechazar |
| `INGRESADO` | `CANCELADO` | Ciudadano dueño | Cancelar |
| `ASIGNADO` | `EN_PROCESO` | Agente del área | Tomar |
| `ASIGNADO` | `CANCELADO` | Ciudadano dueño | Cancelar |
| `EN_PROCESO` | `RESUELTO` | Agente del área | Resolver |
| `RESUELTO` | `CERRADO` | Ciudadano dueño | Confirmar |
| `RESUELTO` | `EN_PROCESO` | Ciudadano dueño | Reabrir |

Pasar a `ASIGNADO` no va por este endpoint: se usa el de asignación.

**Reabrir** (`RESUELTO` → `EN_PROCESO`) tiene reglas propias:

- `observacion` es **obligatoria**: es el motivo. Sin ella, `400 DATOS_INVALIDOS`.
- Hasta `reclamos.reaperturas.maximo` veces (1 por defecto) y dentro de `reclamos.reaperturas.plazo`
  (7 días) desde la última resolución. Fuera de eso, `409 REGLA_NEGOCIO` con el motivo. La cantidad y la
  fecha se calculan del historial.
- El ciudadano necesita el teléfono verificado y la cuenta sin bloquear (`403`), como al crear.
- Cuando ya no se puede reabrir, `accionesDisponibles` no incluye `EN_PROCESO`: la interfaz no ofrece el
  botón.

Errores: `400` estado faltante, desconocido o `ASIGNADO`, o reapertura sin motivo; `403` rol no permitido,
reclamo ajeno o de otra área, teléfono sin verificar o cuenta bloqueada; `404` reclamo o usuario
inexistente; `409` transición inexistente o reapertura fuera de límite.

### `PATCH /api/reclamos/{numero}/asignacion` — asignar o reasignar el área

Solo `ADMINISTRADOR`. Sirve para un reclamo `INGRESADO` (asignación manual) o `ASIGNADO`
(reasignación). El reclamo queda `ASIGNADO` y sin agente.

```json
{
  "areaId": 2,
  "observacion": "Corresponde a Obras Públicas"
}
```

Respuesta `200 OK` con el objeto Reclamo actualizado.

Errores: `400` falta `areaId`; `403` el usuario no es administrador; `404` reclamo, usuario o área
inexistente; `409` el reclamo no está `INGRESADO` ni `ASIGNADO`, el área está inactiva o ya es el
área actual.

### `GET /api/reclamos/{numero}/notificaciones` — avisos generados

Evidencia del patrón Observer: los avisos que generaron los eventos de este reclamo, del más viejo
al más nuevo. Mismos permisos que la consulta del reclamo.

```json
[
  {
    "canal": "INTERNO",
    "destinatario": { "id": 1, "nombreCompleto": "Ana Pérez" },
    "mensaje": "Tu reclamo REC-4F2A91BC fue asignado al area Alumbrado.",
    "fechaEnvio": "2026-10-05T10:00:00"
  },
  {
    "canal": "INTERNO",
    "destinatario": { "id": 3, "nombreCompleto": "Carla Gómez" },
    "mensaje": "El reclamo REC-4F2A91BC fue asignado a tu area Alumbrado.",
    "fechaEnvio": "2026-10-05T10:00:00"
  }
]
```

`canal` es siempre `INTERNO` en el Hito 1: los avisos se guardan en la base y no se envían por
correo ni SMS.

| Evento | A quién se avisa |
| --- | --- |
| `ReclamoCreado` | Ciudadano |
| `ReclamoAsignado` | Ciudadano y agentes activos del área asignada |
| `EstadoReclamoCambiado` | Ciudadano; en la reapertura y el cierre, también el agente a cargo |
| `ReclamoResuelto` | Ciudadano |
| `ReclamoVencido` | Ciudadano y el agente a cargo; si nadie lo tomó, los agentes del área |

## Sesión

El login usa la sesión del servidor, no JWT. La contraseña se guarda con BCrypt y nunca vuelve en una
respuesta. Los cuatro endpoints son `POST`, salvo `yo`, así que llevan `X-XSRF-TOKEN`.

| Endpoint | Qué hace | Respuesta |
| --- | --- | --- |
| `POST /api/auth/login` | Recibe `{ "email", "contrasena" }`. El email no distingue mayúsculas | `200` con el usuario, como en `GET /api/usuarios/{id}`, y la cookie `JSESSIONID`. `401 CREDENCIALES_INVALIDAS` si falla |
| `GET /api/auth/yo` | El usuario de la sesión | `200` con el usuario; `401 NO_AUTENTICADO` sin sesión |
| `POST /api/auth/registro` | Crea un **ciudadano**: `{ "dni", "nombre", "apellido", "email", "contrasena" }`. Contraseña de 8 a 72 caracteres | `201` con el usuario y `Location`. `409 REGLA_NEGOCIO` si el email o el DNI ya existen. No inicia sesión |
| `POST /api/auth/logout` | Invalida la sesión | `204` |

El login le cambia el id a la sesión que traía el navegador, para que nadie que conociera el id anterior
herede el login. La sesión vence a los 30 minutos sin actividad. Los agentes y administradores no se
registran: los da de alta el municipio.

```json
POST /api/auth/login
{ "email": "ana@example.org", "contrasena": "demo1234" }

200
{ "id": 1, "nombreCompleto": "Ana Pérez", "rol": "CIUDADANO", "area": null,
  "telefonoVerificado": "+54 •••••••0001", "bloqueado": false }
```

## Verificación del teléfono

Un ciudadano necesita el teléfono verificado para crear y reabrir reclamos. **Un número verificado vale
para una sola cuenta.** El canal se elige con `reclamos.telegram.modo`:

- `telegram`: el ciudadano abre el enlace al bot, toca **Iniciar** y comparte su contacto. Telegram manda
  el número real del usuario: no se puede escribir uno ajeno. El bot confirma el código y contesta.
- `simulado` (por defecto): sin bot. El número se escribe en la interfaz. Sirve para desarrollar y para la
  demo sin conexión.

| Endpoint | Qué hace | Respuesta |
| --- | --- | --- |
| `POST /api/verificacion-telefono` | Crea un código de un solo uso, de 8 caracteres, que vence a los 10 minutos. Hasta 3 por hora | `201` con `{ "codigo", "enlace", "vence", "modo" }`. `enlace` es `https://t.me/<bot>?start=<codigo>`, o `null` en modo simulado. `403` si no es ciudadano o la cuenta está bloqueada; `429` si pidió demasiados |
| `POST /api/verificacion-telefono/simulada` | Solo en modo simulado: `{ "codigo", "telefono" }`. El código tiene que ser del usuario de la sesión | `200` con el usuario. `400` teléfono inválido; `404` código inexistente o ajeno, o modo `telegram`; `409` código vencido o usado, o número verificado en otra cuenta |

El teléfono se normaliza a E.164 (`+5491155550000`, de 8 a 15 dígitos) y **la API solo lo devuelve
enmascarado** (`+54 •••••••0000`). No viaja a la IA, a RabbitMQ ni a los logs. Verificar otro número
reemplaza al anterior, que queda libre.

```json
POST /api/verificacion-telefono

201
{ "codigo": "K7M2Q9XP", "enlace": "https://t.me/ReclamosQuilmesBot?start=K7M2Q9XP",
  "vence": "2026-11-02T10:10:00", "modo": "telegram" }
```

## Avisos

### `GET /api/notificaciones` — bandeja de avisos del usuario

Los avisos que recibió el usuario de la sesión, de cualquier reclamo, del más nuevo al más
viejo. Cada usuario ve solo los suyos; no hay filtros.

```json
[
  {
    "numeroReclamo": "REC-4F2A91BC",
    "canal": "INTERNO",
    "mensaje": "Tu reclamo REC-4F2A91BC fue asignado al area Alumbrado.",
    "fechaEnvio": "2026-10-05T10:00:00"
  }
]
```

Sin avisos devuelve `[]`. Sin sesión, `401`.

## Tableros

Cifras de reclamos, sin datos de personas. Salen de consultas agregadas en la base: ningún reclamo se carga
completo ([arquitectura](arquitectura.md#tableros)).

### `GET /api/dashboard/publico` — tablero público

**No necesita sesión.** Parámetros opcionales:

| Parámetro | Regla |
| --- | --- |
| `desde`, `hasta` | Días `AAAA-MM-DD`, los dos incluidos. Sin ellos, los últimos 30 días hasta hoy. Hasta 366 días |
| `barrioId`, `categoriaId` | Filtran todas las cifras; tienen que existir |

```json
{
  "desde": "2026-10-05",
  "hasta": "2026-11-03",
  "barrioId": null,
  "categoriaId": null,
  "creadosEnPeriodo": 5,
  "pendientes": 3,
  "resueltos": 1,
  "cerrados": 1,
  "rechazados": 0,
  "cancelados": 1,
  "porcentajeAtendidos": 40.0
}
```

- `creadosEnPeriodo` cuenta los reclamos creados entre `desde` y `hasta`.
- `pendientes` (`INGRESADO`, `ASIGNADO` o `EN_PROCESO`), `resueltos`, `cerrados`, `rechazados` y `cancelados`
  son los de **hoy**, de cualquier fecha de creación.
- `porcentajeAtendidos` es resueltos más cerrados sobre pendientes, resueltos y cerrados, con un decimal. Los
  cancelados y rechazados no cuentan. **Es `null` si no hay ninguno**: la interfaz muestra "sin casos", no un
  porcentaje.

Errores: `400` fechas mal escritas, `desde` posterior a `hasta` o período de más de 366 días; `404` barrio o
categoría inexistente.

### `GET /api/dashboard/gestion` — tablero de gestión

Solo `ADMINISTRADOR` (todas las áreas) y `AGENTE_MUNICIPAL` (la suya). Un ciudadano recibe `403`; sin sesión,
`401`.

```json
{
  "fecha": "2026-11-03T15:00:00",
  "area": null,
  "pendientes": 3,
  "vencidos": 2,
  "sinAsignar": 1,
  "porcentajeVencidos": 66.7,
  "filas": [
    { "area": { "id": 1, "nombre": "Alumbrado" }, "barrio": { "id": 2, "nombre": "Bernal" }, "pendientes": 2, "vencidos": 1 },
    { "area": null, "barrio": { "id": 2, "nombre": "Bernal" }, "pendientes": 1, "vencidos": 1 }
  ]
}
```

- `vencidos` se calcula **por fecha**: pendientes cuya fecha límite ya pasó, aunque la tarea de vencimientos
  todavía no los haya marcado.
- `sinAsignar` son los pendientes sin área (`INGRESADO`). Es `null` para un agente.
- `area` es el área del agente; `null` para el administrador.
- `filas` agrupa por área y barrio, primero las que tienen más vencidos. `area: null` es la fila de los que
  todavía no tienen área.
- `porcentajeVencidos` es `null` sin pendientes ("sin casos").

## Consultas de apoyo

Las categorías, los barrios y las áreas son públicos: la interfaz los pide antes del login.

### `GET /api/usuarios` y `GET /api/usuarios/{id}`

La lista completa solo la ve un administrador; cualquier otro rol recibe `403`. Un usuario puede consultarse
a sí mismo por id; a otro, solo si es administrador.

```json
[
  { "id": 1, "nombreCompleto": "Ana Pérez", "rol": "CIUDADANO", "area": null,
    "telefonoVerificado": "+54 •••••••0001", "bloqueado": false },
  { "id": 3, "nombreCompleto": "Carla Gómez", "rol": "AGENTE_MUNICIPAL", "area": { "id": 1, "nombre": "Alumbrado" },
    "telefonoVerificado": null, "bloqueado": false },
  { "id": 7, "nombreCompleto": "Elena Ruiz", "rol": "ADMINISTRADOR", "area": null,
    "telefonoVerificado": null, "bloqueado": false }
]
```

`area` solo tiene valor para los agentes; `telefonoVerificado`, solo para ciudadanos que lo verificaron, y
siempre enmascarado.

### `PATCH /api/usuarios/{id}/bloqueo` — bloquear o desbloquear una cuenta

Solo `ADMINISTRADOR`, y solo sobre cuentas de ciudadanos. Cuerpo: `{ "bloqueado": true }`. Respuesta
`200` con el usuario.

Una cuenta bloqueada **puede entrar y consultar** sus reclamos, pero no crear, reabrir ni verificar el
teléfono (`403 CUENTA_BLOQUEADA`). Errores: `403` si no es administrador, `404` usuario inexistente,
`409` si la cuenta no es de un ciudadano.

### `GET /api/categorias`

```json
[
  { "id": 1, "nombre": "Luminaria rota", "descripcion": "Farol apagado o dañado", "slaHoras": 48, "prioridadBase": "MEDIA" }
]
```

### `GET /api/barrios`

```json
[
  { "id": 2, "nombre": "Bernal", "municipio": "Quilmes" }
]
```

### `GET /api/areas`

Para el formulario de asignación manual.

```json
[
  {
    "id": 1,
    "nombre": "Alumbrado",
    "activa": true,
    "categorias": [{ "id": 1, "nombre": "Luminaria rota" }],
    "barrios": [{ "id": 1, "nombre": "Quilmes Centro" }, { "id": 2, "nombre": "Bernal" }]
  }
]
```

## Ubicaciones

### `GET /api/ubicaciones/sugerencias` — buscar la dirección en el mapa

Antes del alta. El backend busca la dirección en [Georef](https://www.argentina.gob.ar/georef), la API
oficial de normalización de direcciones, dentro del municipio y la provincia del barrio. Exige sesión,
porque cada búsqueda consulta una API externa.

`GET /api/ubicaciones/sugerencias?direccion=Belgrano%20450&barrioId=2`

| Parámetro | Regla |
| --- | --- |
| `direccion` | Obligatoria, de 3 a 200 caracteres |
| `barrioId` | Obligatorio; debe existir |

```json
{
  "disponible": true,
  "sugerencias": [
    {
      "direccionNormalizada": "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires",
      "latitud": -34.73614578,
      "longitud": -58.3228998575
    }
  ]
}
```

- Devuelve hasta 5 sugerencias, solo las que tienen coordenadas dentro del recuadro del municipio. Una
  dirección puede ser ambigua (Belgrano 450 existe en Bernal Oeste y en Quilmes): el ciudadano elige.
- `sugerencias` vacía con `disponible: true`: Georef no encontró la dirección.
- `disponible: false`: Georef no respondió en 3 segundos o respondió un error. **No es un error HTTP**: el
  reclamo se puede crear igual, sin `ubicacion`.
- La misma búsqueda en el mismo municipio se responde de memoria durante 10 minutos, sin volver a Georef.

Errores: `400` parámetros inválidos, `404` barrio inexistente.

El enlace a Google Maps lo arma la interfaz con las coordenadas
(`https://www.google.com/maps/search/?api=1&query=<latitud>%2C<longitud>`); no hace falta clave.

## Datos semilla acordados

El PR 2 carga estos datos con estos ids; los mocks del frontend usan los mismos.

Municipio: Quilmes, provincia de Buenos Aires.

| Id | Barrio |
| --- | --- |
| 1 | Quilmes Centro |
| 2 | Bernal |
| 3 | Ezpeleta |

| Id | Categoría | SLA en horas | Prioridad base |
| --- | --- | --- | --- |
| 1 | Luminaria rota | 48 | MEDIA |
| 2 | Bache | 120 | BAJA |
| 3 | Residuos | 72 | MEDIA |

| Id | Área | Categorías | Barrios |
| --- | --- | --- | --- |
| 1 | Alumbrado | Luminaria rota | Quilmes Centro, Bernal |
| 2 | Obras Públicas | Bache | Quilmes Centro, Bernal, Ezpeleta |
| 3 | Higiene Urbana | Residuos | Quilmes Centro, Bernal |
| 4 | Mantenimiento Vial | Bache | Bernal |

| Id | Usuario | Email para entrar | Rol | Área |
| --- | --- | --- | --- | --- |
| 1 | Ana Pérez | `ana@example.org` | Ciudadano | |
| 2 | Bruno Díaz | `bruno@example.org` | Ciudadano | |
| 3 | Carla Gómez | `carla@example.org` | Agente | Alumbrado |
| 4 | Diego Sosa | `diego@example.org` | Agente | Obras Públicas |
| 5 | Fabián Luna | `fabian@example.org` | Agente | Higiene Urbana |
| 6 | Gabriela Paz | `gabriela@example.org` | Agente | Mantenimiento Vial |
| 7 | Elena Ruiz | `elena@example.org` | Administrador | |

Los siete entran con la contraseña de desarrollo **`demo1234`**. Es solo para la semilla local: la base
guarda su hash BCrypt. Ana (`+5491100000001`) y Bruno (`+5491100000002`) ya tienen el teléfono
verificado, con números ficticios, para crear reclamos sin pasar por la verificación.

Con esta semilla se pueden demostrar los casos que dependen del territorio:

| Caso | Reclamo | Resultado esperado |
| --- | --- | --- |
| Asignación automática | Luminaria rota en Bernal | `ASIGNADO` a Alumbrado |
| Sin área compatible | Luminaria rota o Residuos en Ezpeleta | `INGRESADO`; el administrador asigna a mano |
| Dos áreas candidatas | Bache en Bernal | Obras Públicas o Mantenimiento Vial, según la estrategia de asignación activa |
