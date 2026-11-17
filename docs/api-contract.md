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
`/api/municipios` y `/api/areas`), que la interfaz necesita antes de que alguien entre. Sin sesión, la respuesta
es `401 NO_AUTENTICADO`. Qué puede hacer cada usuario lo decide su rol, guardado en la base: la sesión solo
dice quién es.

### Enumerados

| Enumerado | Valores |
| --- | --- |
| `rol` | `CIUDADANO`, `AGENTE_MUNICIPAL`, `ADMINISTRADOR` |
| `estado` | `INGRESADO`, `ASIGNADO`, `EN_PROCESO`, `RESUELTO`, `CERRADO`, `RECHAZADO`, `CANCELADO` |
| `prioridad` | `BAJA`, `MEDIA`, `ALTA`, `CRITICA` |
| `origenPrioridad` | `CATEGORIA`, `PALABRAS_CLAVE`, `IA`, `RESPALDO`, `PENDIENTE_IA` |
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
| 404 | `RECURSO_NO_ENCONTRADO` | No existe el usuario, reclamo, municipio, categoría o área | `RecursoNoEncontradoException` |
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

Lo devuelven la creación, la consulta por número, el cambio de estado, la asignación y la recategorización.

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
  "municipio": { "id": 1, "nombre": "Quilmes" },
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
  "accionesDisponibles": ["CANCELADO"],
  "categoriaPropuesta": null,
  "categoriaSugerida": null,
  "motivoSugerencia": null
}
```

- `ubicacion.estado` es `CONFIRMADA` cuando el ciudadano eligió una sugerencia del geocodificador, con
  coordenadas, dirección normalizada y proveedor; si no, `PENDIENTE` y los demás campos en `null`.
- `foto` es la ruta de la foto del problema ([`GET /api/reclamos/{numero}/foto`](#get-apireclamosnumerofoto--foto-del-reclamo)).
  Es `null` en los reclamos creados antes de que la foto fuera obligatoria.
- `cantidadApoyos` es cuántos vecinos apoyan el reclamo ([apoyos](#apoyos-y-posibles-duplicados)). No cambia la
  prioridad.
- `origenPrioridad` dice qué decidió la prioridad: `CATEGORIA`, `PALABRAS_CLAVE`, `IA`, `RESPALDO` (la IA
  no respondió y se usó la de la categoría) o `PENDIENTE_IA` (por ahora vale la de la categoría y la IA la
  está revisando por la cola; en unos segundos pasa a `IA`, `CATEGORIA` o `RESPALDO`, sin que el reclamo
  cambie de estado). `motivoPrioridad` la explica. Es `null` cuando la prioridad es
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
- **"Otro problema" (PR 26):**
  - `categoriaPropuesta` es qué tipo de problema escribió el vecino al elegir la categoría "Otro problema".
    Es `null` en las demás y se conserva después de recategorizar.
  - `categoriaSugerida` es la categoría que sugirió la IA, como `{ "id", "nombre" }`. Es `null` si la IA no
    sugirió ninguna, si todavía no respondió o si el reclamo no es de "Otro problema".
  - `motivoSugerencia` explica la sugerencia, o por qué no hay. Es `null` mientras la IA no respondió.
  - Ejemplo: `"categoriaPropuesta": "Baldosas levantadas"`,
    `"categoriaSugerida": { "id": 7, "nombre": "Vereda rota" }`,
    `"motivoSugerencia": "Las baldosas sueltas son un problema de la vereda."`.

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
  "municipio": { "id": 1, "nombre": "Quilmes" },
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
  "municipioId": 1,
  "descripcion": "Farol apagado frente a la plaza",
  "direccion": "Belgrano 450",
  "ubicacion": {
    "latitud": -34.73614578,
    "longitud": -58.3228998575,
    "direccionNormalizada": "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires"
  },
  "categoriaPropuesta": null
}
```

| Campo | Regla |
| --- | --- |
| `categoriaId` | Obligatorio; debe existir |
| `municipioId` | Obligatorio; debe existir. Es el `municipio.id` de la sugerencia elegida o, si el mapa no respondió o el vecino no eligió ninguna, el que eligió de [la lista](#get-apimunicipios) |
| `descripcion` | Obligatoria, hasta 1000 caracteres |
| `direccion` | Obligatoria, hasta 200 caracteres. Es lo que escribió el ciudadano y se guarda siempre |
| `categoriaPropuesta` | Hasta 80 caracteres. **Obligatoria si la categoría es "Otro problema"** (`otra: true` en [`GET /api/categorias`](#get-apicategorias)); en las demás se ignora. Es qué tipo de problema es, en palabras del vecino (PR 26) |
| `ubicacion` | Opcional: la sugerencia de [`GET /api/ubicaciones/sugerencias`](#get-apiubicacionessugerencias--buscar-la-dirección-en-el-mapa) que eligió el ciudadano, con sus tres campos. Las coordenadas tienen que caer en el municipio del reclamo: primero se mira el recuadro del municipio y después Georef confirma el partido con `GET /ubicacion` (si Georef no responde, alcanza el recuadro). Sin `ubicacion`, el reclamo queda con la ubicación `PENDIENTE` |

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

- Si un área activa del municipio atiende la categoría, el reclamo vuelve `ASIGNADO` con esa área. Cada
  municipio tiene sus áreas: el Alumbrado de Quilmes no atiende reclamos de Avellaneda.
- Si ninguna lo cubre, vuelve `INGRESADO` con `area: null`. Sigue siendo `201`.
- **"Otro problema" no la atiende ningún área:** el reclamo vuelve `INGRESADO`. Si la IA está activa, el alta
  deja en la outbox un pedido de sugerencia de categoría ([cola `reclamos.categoria`](contratos/mensajes.md)).
  Unos segundos después, `categoriaSugerida` y `motivoSugerencia` tienen la respuesta. Un administrador
  recategoriza con [`PATCH .../categoria`](#patch-apireclamosnumerocategoria--recategorizar).

Errores:

- `400`:
  - "Otro problema" sin `categoriaPropuesta` (`Elegiste "Otro problema": contanos qué tipo de problema es.`).
  - Datos inválidos o ubicación fuera del municipio (`La ubicación elegida queda fuera de Quilmes.` o, si
    Georef la ubica en otro, `La ubicación elegida queda en Avellaneda, no en Quilmes.`).
  - Sin foto (`El reclamo necesita una foto del problema.`).
  - Un archivo que no es JPEG ni PNG.
- `403 ACCESO_DENEGADO`: el usuario no es ciudadano.
- `403 TELEFONO_NO_VERIFICADO` y `403 CUENTA_BLOQUEADA`.
- `404`: usuario, municipio o categoría inexistente.
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

Antes de crear un reclamo con la ubicación marcada en el mapa, la interfaz busca reclamos abiertos a pocos
metros. Desde el PR 27 la IA compara el texto del reclamo nuevo con los cercanos **de cualquier categoría** y
marca el que describe el mismo problema. Si es así, el vecino **se asocia** (apoya) en lugar de crear otro, y
desde ese momento recibe los avisos de ese reclamo.

### `POST /api/reclamos/candidatos-duplicados` — posibles duplicados del alta (PR 27)

Es la que usa la interfaz. Va por `POST` porque lleva la descripción, que no conviene poner en la URL.

```json
{ "categoriaId": 1, "descripcion": "El farol de la esquina no prende", "latitud": -34.73614578, "longitud": -58.3228998575 }
```

| Campo | Regla |
| --- | --- |
| `categoriaId` | Obligatorio; debe existir. Es la categoría que eligió el vecino |
| `descripcion` | Hasta 1000 caracteres. Sin descripción no se compara: quedan los de la misma categoría |
| `latitud`, `longitud` | Obligatorios: el punto elegido en el mapa |

1. **Busca los cercanos.** Toma los reclamos abiertos con la ubicación confirmada a `reclamos.duplicados.radio-metros`
   (50 m), de cualquier categoría. Se queda con los `maximo-comparados` más próximos (10).
2. **La IA los compara.** Con la estrategia `ia`, le pregunta a REST 2 ([`POST /api/duplicados/comparar`](contratos/README.md#rest-2-post-apiduplicadoscomparar))
   cuál describe el mismo problema que el nuevo. A REST 2 solo le llegan la categoría y la descripción de cada
   uno, con ids `1`, `2`…: ni números de reclamo, ni nombres, ni direcciones, ni fotos. La espera es la del alta
   (4 s), con su propio circuito.
3. **Arma la lista.** Si la IA eligió uno, va primero con `masParecido: true`, aunque sea de otra categoría.
   Después van los de la misma categoría, del más cercano al más lejano, hasta 5 en total.
   - Si la IA dijo que ninguno es el mismo, quedan solo los de la misma categoría.
   - Si no hay IA, o no respondió, quedan los de la misma categoría y `comparadoPorIA` es `false`.

```json
{
  "comparadoPorIA": true,
  "motivo": "Los dos hablan del mismo farol apagado en la esquina.",
  "candidatos": [
    {
      "numero": "REC-7C4046A4",
      "estado": "ASIGNADO",
      "fechaCreacion": "2026-11-03T17:34:00",
      "descripcion": "No hay luz en la esquina, el poste está apagado.",
      "direccion": "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires",
      "foto": "/api/reclamos/REC-7C4046A4/foto",
      "distanciaMetros": 13,
      "cantidadApoyos": 0,
      "apoyadoPorMi": false,
      "propio": false,
      "categoria": { "id": 8, "nombre": "Otro problema" },
      "masParecido": true
    }
  ]
}
```

Errores: `400` datos inválidos; `401` sin sesión; `404` categoría inexistente. Una falla de la IA no es un
error: responde `200` sin comparación.

### `GET /api/reclamos/candidatos-duplicados` — posibles duplicados de la misma categoría

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
    "propio": false,
    "categoria": { "id": 1, "nombre": "Luminaria rota" },
    "masParecido": false
  }
]
```

Es la búsqueda del PR 17, sin IA: solo la categoría pedida y `masParecido` siempre `false`. Se mantiene por
compatibilidad.

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
prioridad**. Desde el PR 27, apoyar es **asociarse**: el vecino recibe un aviso al asociarse ("Te sumaste al
reclamo REC-…") y otro cada vez que el reclamo cambia de estado o se resuelve ("El reclamo REC-… que apoyaste
paso de ASIGNADO a EN_PROCESO."). Retirar el apoyo corta los avisos. La base guarda un apoyo por ciudadano y reclamo (restricción única); si dos pedidos iguales
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
inexistente; `409` el reclamo no está `INGRESADO` ni `ASIGNADO`, el área está inactiva, ya es el
área actual o es de otro municipio (`El área Alumbrado es de Quilmes y el reclamo es de Avellaneda.`).

### `PATCH /api/reclamos/{numero}/categoria` — recategorizar

PR 26. Solo `ADMINISTRADOR`, y solo con el reclamo `INGRESADO`, es decir, antes de que tenga área. Sirve sobre
todo para los reclamos de "Otro problema", con la categoría que sugirió la IA. También corrige un reclamo que
entró con la categoría equivocada.

```json
{
  "categoriaId": 7,
  "observacion": "Como sugirió la IA"
}
```

- **El plazo** se vuelve a calcular desde la fecha de creación, con el SLA de la categoría nueva. Si con el plazo
  nuevo todavía no venció, `vencido` vuelve a `false`.
- **La prioridad no baja:** queda la mayor entre la actual y la prioridad base de la categoría nueva.
- **El historial** suma un registro sin cambio de estado (`INGRESADO` a `INGRESADO`), con la observación
  `Categoría: Otro problema → Vereda rota. Como sugirió la IA`.
- **Asignación:** después, el sistema intenta asignarlo como en el alta. Si un área del municipio atiende la
  categoría nueva, el reclamo vuelve `ASIGNADO`.
- **Avisos:** el vecino recibe "Revisamos tu reclamo REC-…: paso de Otro problema a Vereda rota." Si quedó
  asignado, también sale el aviso de asignación.

Respuesta `200 OK` con el objeto Reclamo actualizado.

Errores:

- `400`: falta `categoriaId`.
- `403`: el usuario no es administrador.
- `404`: reclamo o categoría inexistente.
- `409`:
  - El reclamo ya tiene área.
  - La categoría es la misma.
  - La categoría nueva es "Otro problema".

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
    "fechaEnvio": "2026-10-05T10:00:00",
    "abrible": true
  }
]
```

Sin avisos devuelve `[]`. Sin sesión, `401`. `abrible` (PR 27) es `false` en los avisos de un reclamo ajeno que
el vecino apoya: los lee, pero el detalle sigue siendo del autor, y la interfaz no ofrece abrirlo.

## Tableros

Cifras de reclamos, sin datos de personas. Salen de consultas agregadas en la base: ningún reclamo se carga
completo ([arquitectura](arquitectura.md#tableros)).

### `GET /api/dashboard/publico` — tablero público

**No necesita sesión.** Parámetros opcionales:

| Parámetro | Regla |
| --- | --- |
| `desde`, `hasta` | Días `AAAA-MM-DD`, los dos incluidos. Sin ellos, los últimos 30 días hasta hoy. Hasta 366 días |
| `municipioId`, `categoriaId` | Filtran todas las cifras; tienen que existir |

```json
{
  "desde": "2026-10-05",
  "hasta": "2026-11-03",
  "municipioId": null,
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

Errores: `400` fechas mal escritas, `desde` posterior a `hasta` o período de más de 366 días; `404` municipio o
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
    { "area": { "id": 1, "nombre": "Alumbrado" }, "municipio": { "id": 1, "nombre": "Quilmes" }, "pendientes": 2, "vencidos": 1 },
    { "area": null, "municipio": { "id": 2, "nombre": "Avellaneda" }, "pendientes": 1, "vencidos": 1 }
  ]
}
```

- `vencidos` se calcula **por fecha**: pendientes cuya fecha límite ya pasó, aunque la tarea de vencimientos
  todavía no los haya marcado.
- `sinAsignar` son los pendientes sin área (`INGRESADO`). Es `null` para un agente.
- `area` es el área del agente; `null` para el administrador.
- `filas` agrupa por área y municipio, primero las que tienen más vencidos. `area: null` es la fila de los que
  todavía no tienen área.
- `porcentajeVencidos` es `null` sin pendientes ("sin casos").

## Consultas de apoyo

Las categorías, los municipios y las áreas son públicos: la interfaz los pide antes del login.

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
  { "id": 1, "nombre": "Luminaria rota", "descripcion": "Farol apagado o dañado", "slaHoras": 48, "prioridadBase": "MEDIA", "otra": false },
  { "id": 8, "nombre": "Otro problema", "descripcion": "Ninguna categoría corresponde: el vecino escribe qué tipo de problema es", "slaHoras": 72, "prioridadBase": "MEDIA", "otra": true }
]
```

`otra` (PR 26) marca la categoría "Otro problema": con ella, el alta pide `categoriaPropuesta`. La lista viene
ordenada por nombre; la interfaz muestra "Otro problema" al final.

### `GET /api/municipios`

Los municipios donde se pueden hacer reclamos (PR 25: reemplaza a `GET /api/barrios`). La interfaz los ofrece
para elegir a mano cuando el mapa no responde o el vecino no elige ninguna dirección.

```json
[
  { "id": 1, "nombre": "Quilmes", "provincia": "Buenos Aires" },
  { "id": 2, "nombre": "Avellaneda", "provincia": "Buenos Aires" },
  { "id": 3, "nombre": "Berazategui", "provincia": "Buenos Aires" }
]
```

### `GET /api/areas`

Para el formulario de asignación manual. Cada área es de un municipio, que es su jurisdicción: la interfaz
ofrece solo las del municipio del reclamo, y el backend rechaza con `409` las de otro.

```json
[
  {
    "id": 1,
    "nombre": "Alumbrado",
    "activa": true,
    "municipio": { "id": 1, "nombre": "Quilmes" },
    "categorias": [{ "id": 1, "nombre": "Luminaria rota" }]
  }
]
```

## Ubicaciones

### `GET /api/ubicaciones/sugerencias` — buscar la dirección en el mapa

Antes del alta. El backend busca la dirección en [Georef](https://www.argentina.gob.ar/georef), la API
oficial de normalización de direcciones, **una vez por municipio** y en paralelo. Cada sugerencia dice de qué
municipio es: elegirla define el municipio del reclamo. Exige sesión, porque cada búsqueda consulta una API
externa.

`GET /api/ubicaciones/sugerencias?direccion=Belgrano%20450`

| Parámetro | Regla |
| --- | --- |
| `direccion` | Obligatoria, de 3 a 200 caracteres |

```json
{
  "disponible": true,
  "sugerencias": [
    {
      "direccionNormalizada": "GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires",
      "latitud": -34.73614578,
      "longitud": -58.3228998575,
      "municipio": { "id": 1, "nombre": "Quilmes" }
    }
  ]
}
```

- Devuelve hasta 5 sugerencias, solo las que tienen coordenadas dentro del recuadro de su municipio. Una
  dirección puede ser ambigua (hay un Belgrano 450 en cada municipio): el ciudadano elige.
- `sugerencias` vacía con `disponible: true`: Georef no encontró la dirección.
- `disponible: false`: Georef no respondió en 3 segundos, o respondió un error, para ningún municipio. **No
  es un error HTTP**: el ciudadano elige el municipio de la lista y el reclamo se crea igual, sin `ubicacion`.
  Si falló para algunos municipios, devuelve lo que encontró en los demás.
- La misma búsqueda en el mismo municipio se responde de memoria durante 10 minutos, sin volver a Georef.

Errores: `400` parámetros inválidos.

El enlace a Google Maps lo arma la interfaz con las coordenadas
(`https://www.google.com/maps/search/?api=1&query=<latitud>%2C<longitud>`); no hace falta clave.

## Datos semilla acordados

El PR 2 carga estos datos con estos ids; los mocks del frontend usan los mismos. Desde el PR 25 la semilla
es por municipio (`datos-dev-v2`): una base anterior se borra una vez con `docker compose down -v`.

| Id | Municipio | Provincia |
| --- | --- | --- |
| 1 | Quilmes | Buenos Aires |
| 2 | Avellaneda | Buenos Aires |
| 3 | Berazategui | Buenos Aires |

Las categorías 4 a 8 son del PR 26. Van en un bloque aparte de la semilla, con su propia marca
(`categorias-pr26`): una base del PR 25 las recibe al arrancar, sin borrarla.

| Id | Categoría | SLA en horas | Prioridad base | `otra` |
| --- | --- | --- | --- | --- |
| 1 | Luminaria rota | 48 | MEDIA | |
| 2 | Bache | 120 | BAJA | |
| 3 | Residuos | 72 | MEDIA | |
| 4 | Semáforo averiado | 24 | ALTA | |
| 5 | Árbol caído o con riesgo | 48 | ALTA | |
| 6 | Pérdida de agua | 48 | ALTA | |
| 7 | Vereda rota | 168 | BAJA | |
| 8 | Otro problema | 72 | MEDIA | sí |

| Id | Área | Municipio | Categorías |
| --- | --- | --- | --- |
| 1 | Alumbrado | Quilmes | Luminaria rota, Semáforo averiado |
| 2 | Obras Públicas | Quilmes | Bache, Pérdida de agua, Vereda rota |
| 3 | Higiene Urbana | Quilmes | Residuos, Árbol caído o con riesgo |
| 4 | Mantenimiento Vial | Quilmes | Bache, Vereda rota |
| 5 | Obras Públicas | Avellaneda | Bache, Pérdida de agua, Vereda rota |
| 6 | Higiene Urbana | Avellaneda | Residuos, Árbol caído o con riesgo |
| 7 | Alumbrado | Berazategui | Luminaria rota, Semáforo averiado |
| 8 | Obras Públicas | Berazategui | Bache, Pérdida de agua, Vereda rota |
| 9 | Higiene Urbana | Berazategui | Residuos, Árbol caído o con riesgo |

"Otro problema" no la atiende ningún área.

| Id | Usuario | Email para entrar | Rol | Área |
| --- | --- | --- | --- | --- |
| 1 | Ana Pérez | `ana@example.org` | Ciudadano | |
| 2 | Bruno Díaz | `bruno@example.org` | Ciudadano | |
| 3 | Carla Gómez | `carla@example.org` | Agente | Alumbrado |
| 4 | Diego Sosa | `diego@example.org` | Agente | Obras Públicas |
| 5 | Fabián Luna | `fabian@example.org` | Agente | Higiene Urbana |
| 6 | Gabriela Paz | `gabriela@example.org` | Agente | Mantenimiento Vial |
| 7 | Elena Ruiz | `elena@example.org` | Administrador | |
| 8 | Hugo Medina | `hugo@example.org` | Agente | Obras Públicas (Avellaneda) |

Los ocho entran con la contraseña de desarrollo **`demo1234`**. Es solo para la semilla local: la base
guarda su hash BCrypt. Ana (`+5491100000001`) y Bruno (`+5491100000002`) ya tienen el teléfono
verificado, con números ficticios, para crear reclamos sin pasar por la verificación.

Con esta semilla se pueden demostrar los casos que dependen del territorio:

| Caso | Reclamo | Resultado esperado |
| --- | --- | --- |
| Asignación automática | Luminaria rota en Quilmes | `ASIGNADO` a Alumbrado (1) |
| El mismo pedido en otro municipio | Bache en Avellaneda | `ASIGNADO` a Obras Públicas de Avellaneda (5), no a la de Quilmes |
| Sin área compatible | Luminaria rota en Avellaneda | `INGRESADO`; el administrador asigna a mano un área de Avellaneda |
| Otro problema | "Baldosas levantadas" en Quilmes | `INGRESADO`; la IA sugiere Vereda rota y, al recategorizar, queda `ASIGNADO` a Obras Públicas (2) |
| Dos áreas candidatas | Bache en Quilmes | Obras Públicas o Mantenimiento Vial, según la estrategia de asignación activa |
