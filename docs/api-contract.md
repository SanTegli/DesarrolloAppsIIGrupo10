# Contrato REST — Hito 1

Contrato entre el backend y el frontend. Lo implementan los controladores de `reclamos-app` y lo
consume `frontend/src/api/`. La API simulada del frontend (`npm run mock`) sigue este mismo documento.

Si alguien necesita cambiar algo de acá, lo avisa al grupo antes: es el único archivo del que
dependen tres personas a la vez.

## Convenciones

| Tema | Regla |
| --- | --- |
| URL base | `http://localhost:8080/api` |
| Formato | JSON en UTF-8, `Content-Type: application/json` |
| Fechas | ISO-8601 sin zona horaria: `2026-10-05T10:00:00` |
| Identidad | Encabezado `X-Usuario-Id` con el id numérico del usuario. No hay login en el Hito 1 |
| Número de reclamo | `REC-` más ocho caracteres hexadecimales en mayúscula: `REC-4F2A91BC` |

`X-Usuario-Id` es obligatorio en todos los endpoints de `/api/reclamos`. Los endpoints de consulta
de usuarios, barrios, categorías y áreas no lo piden, porque el selector de usuario los necesita
antes de saber quién es el usuario.

### Enumerados

| Enumerado | Valores |
| --- | --- |
| `rol` | `CIUDADANO`, `AGENTE_MUNICIPAL`, `ADMINISTRADOR` |
| `estado` | `INGRESADO`, `ASIGNADO`, `EN_PROCESO`, `RESUELTO`, `CERRADO`, `RECHAZADO`, `CANCELADO` |
| `prioridad` | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |

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
| 400 | `DATOS_INVALIDOS` | Falta un campo, formato incorrecto, falta `X-Usuario-Id` | `DatosInvalidosException` |
| 403 | `ACCESO_DENEGADO` | El usuario existe pero su rol no puede hacer la operación, o el reclamo no es suyo ni de su área | `AccesoDenegadoException` |
| 404 | `RECURSO_NO_ENCONTRADO` | No existe el usuario, reclamo, barrio, categoría o área | `RecursoNoEncontradoException` |
| 409 | `REGLA_NEGOCIO` | Transición de estado inexistente u otra regla del negocio | `ReglaNegocioException` |
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
  "estado": "ASIGNADO",
  "prioridad": "MEDIA",
  "vencido": false,
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
  "direccion": "Belgrano 450"
}
```

| Campo | Regla |
| --- | --- |
| `categoriaId` | Obligatorio; debe existir |
| `barrioId` | Obligatorio; debe existir |
| `descripcion` | Obligatoria, hasta 1000 caracteres |
| `direccion` | Obligatoria, hasta 200 caracteres |

Respuesta `201 Created` con el encabezado `Location: /api/reclamos/{numero}` y el objeto Reclamo.

- Si un área activa atiende la categoría y tiene jurisdicción sobre el barrio, el reclamo vuelve
  `ASIGNADO` con esa área.
- Si ninguna lo cubre, vuelve `INGRESADO` con `area: null`. Sigue siendo `201`.

Errores: `400` datos inválidos, `403` el usuario no es ciudadano, `404` usuario, barrio o categoría
inexistente.

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

## Avisos

### `GET /api/notificaciones` — bandeja de avisos del usuario

Los avisos que recibió el usuario de `X-Usuario-Id`, de cualquier reclamo, del más nuevo al más
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

Sin avisos devuelve `[]`. Errores: `400` falta `X-Usuario-Id` o no es un número positivo; `404` el
usuario no existe.

## Consultas de apoyo

No piden `X-Usuario-Id`.

### `GET /api/usuarios` y `GET /api/usuarios/{id}`

Alimentan el selector de usuario.

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

| Id | Usuario | Rol | Área |
| --- | --- | --- | --- |
| 1 | Ana Pérez | Ciudadano | |
| 2 | Bruno Díaz | Ciudadano | |
| 3 | Carla Gómez | Agente | Alumbrado |
| 4 | Diego Sosa | Agente | Obras Públicas |
| 5 | Fabián Luna | Agente | Higiene Urbana |
| 6 | Gabriela Paz | Agente | Mantenimiento Vial |
| 7 | Elena Ruiz | Administrador | |

Con esta semilla se pueden demostrar los casos que dependen del territorio:

| Caso | Reclamo | Resultado esperado |
| --- | --- | --- |
| Asignación automática | Luminaria rota en Bernal | `ASIGNADO` a Alumbrado |
| Sin área compatible | Luminaria rota o Residuos en Ezpeleta | `INGRESADO`; el administrador asigna a mano |
| Dos áreas candidatas | Bache en Bernal | Obras Públicas o Mantenimiento Vial, según la estrategia de asignación activa |
