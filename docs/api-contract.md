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
| 403 | `CSRF_INVALIDO` | Un `POST` o `PATCH` sin el encabezado `X-XSRF-TOKEN`, o con un valor distinto al de la cookie | `RespuestasDeSeguridad` (Spring Security) |
| 404 | `RECURSO_NO_ENCONTRADO` | No existe el usuario, reclamo, barrio, categoría o área | `RecursoNoEncontradoException` |
| 409 | `REGLA_NEGOCIO` | Transición de estado inexistente u otra regla del negocio | `ReglaNegocioException` |
| 409 | `CONFLICTO_CONCURRENTE` | Otra operación modificó el mismo reclamo mientras se procesaba esta; hay que volver a cargarlo | Bloqueo optimista de JPA |
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

Solo `CIUDADANO`. El reclamo queda a nombre del usuario del encabezado.

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

- Si un área activa atiende la categoría y tiene jurisdicción sobre el barrio, el reclamo vuelve
  `ASIGNADO` con esa área.
- Si ninguna lo cubre, vuelve `INGRESADO` con `area: null`. Sigue siendo `201`.

Errores: `400` datos inválidos o ubicación fuera del municipio (`La ubicación elegida queda fuera de
Quilmes.`), `403` el usuario no es ciudadano, `404` usuario, barrio o categoría inexistente.

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

Errores: `400` estado faltante, desconocido o `ASIGNADO`; `403` rol no permitido, reclamo ajeno o de
otra área; `404` reclamo o usuario inexistente; `409` transición inexistente.

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
{ "id": 1, "nombreCompleto": "Ana Pérez", "rol": "CIUDADANO", "area": null }
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

## Consultas de apoyo

Las categorías, los barrios y las áreas son públicos: la interfaz los pide antes del login.

### `GET /api/usuarios` y `GET /api/usuarios/{id}`

La lista completa solo la ve un administrador; cualquier otro rol recibe `403`. Un usuario puede consultarse
a sí mismo por id; a otro, solo si es administrador.

```json
[
  { "id": 1, "nombreCompleto": "Ana Pérez", "rol": "CIUDADANO", "area": null },
  { "id": 3, "nombreCompleto": "Carla Gómez", "rol": "AGENTE_MUNICIPAL", "area": { "id": 1, "nombre": "Alumbrado" } },
  { "id": 7, "nombreCompleto": "Elena Ruiz", "rol": "ADMINISTRADOR", "area": null }
]
```

`area` solo tiene valor para los agentes.

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
guarda su hash BCrypt.

Con esta semilla se pueden demostrar los casos que dependen del territorio:

| Caso | Reclamo | Resultado esperado |
| --- | --- | --- |
| Asignación automática | Luminaria rota en Bernal | `ASIGNADO` a Alumbrado |
| Sin área compatible | Luminaria rota o Residuos en Ezpeleta | `INGRESADO`; el administrador asigna a mano |
| Dos áreas candidatas | Bache en Bernal | Obras Públicas o Mantenimiento Vial, según la estrategia de asignación activa |
