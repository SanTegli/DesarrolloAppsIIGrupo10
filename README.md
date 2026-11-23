# Sistema de Reclamos Urbanos

[![CI](https://github.com/SanTegli/DesarrolloAppsIIGrupo10/actions/workflows/ci.yml/badge.svg)](https://github.com/SanTegli/DesarrolloAppsIIGrupo10/actions/workflows/ci.yml)

TPO de Desarrollo de Aplicaciones II (UADE) — Grupo 10. Docente: Mg. Christian Parkinson.

Los ciudadanos reportan problemas en la vía pública (baches, luminarias rotas, residuos), el sistema
asigna cada reclamo al área municipal que corresponde y los agentes lo resuelven.

El repositorio tiene las tres etapas del trabajo práctico:

- **Primera Parte (Hito 1):** aplicación empresarial en capas con los patrones Factory, Repository,
  Strategy, Observer y Facade.
- **Segunda Parte:** servicio SOAP de cobertura, servicio REST de prioridad con IA (Gemini), ubicación de
  las direcciones con Georef, avisos por correo con RabbitMQ y verificación del teléfono con Telegram.
- **Integrador:** login, foto, posibles duplicados y apoyos, tableros, los
  [requerimientos](docs/requerimientos.md) (incluidos los de la IA) y la prueba de carga básica.

## Estructura

```text
backend/                    Proyecto Maven multimódulo (Java 21, Spring Boot 3)
  reclamos-comun/           Utilidades: excepciones de negocio y validación
  reclamos-dominio/         Modelo, reglas, fábrica, eventos y contratos. Java puro, sin Spring
  reclamos-persistencia/    Adaptadores JPA de los Repository
  reclamos-app/             Servicios, fachada, eventos y API REST
frontend/                   Interfaz React + Vite
  src/api/                  Única capa que habla con el backend
  src/componentes/          Pantallas: ingreso, verificación del teléfono, listado, alta con posibles
                            duplicados, detalle, avisos, administración y tableros
  mock/                     API simulada para trabajar sin Java ni MySQL
servicios/                  Servicios de integración de la Segunda Parte, cada uno un proceso aparte
  servicio-cobertura/       SOAP: qué áreas de cada municipio atienden cada categoría (WSDL en el puerto 8081)
  servicio-prioridad/       REST 2: prioridad sugerida por IA con Gemini (Swagger en el puerto 8082)
  servicio-notificaciones/  Consumidor de RabbitMQ: manda los avisos por correo (Mailpit)
docs/                       Requerimientos, arquitectura, patrones, contratos, despliegue y evidencias
  contratos/                Contratos de integración: XSD de SOAP, OpenAPI de REST 2 y mensajes
  openapi/                  Contrato OpenAPI de REST 1, generado del código
scripts/                    Verificación de la demo de punta a punta y prueba de carga (Node, sin dependencias);
                            capturas/: capturas del sistema real para las evidencias (Playwright)
docker-compose.yml          MySQL para desarrollo; backend, frontend y servicios para la demo
```

Las dependencias entre módulos van en un solo sentido: `app → persistencia → dominio → comun`.

## Requisitos

- JDK 21
- Docker, para MySQL
- Node.js 22 (o 20.19 en adelante), para el frontend

No hace falta instalar Maven: el proyecto trae el wrapper (`mvnw`).

## Cómo correr los tests

Los tests usan H2 en memoria. No necesitan Docker ni MySQL.

```bash
cd backend
./mvnw test
```

En Windows, `.\mvnw.cmd test`. Los servicios de integración tienen su propio proyecto:

```bash
cd servicios
./mvnw verify
```

GitHub Actions corre los tests del backend, de los servicios y del frontend en cada pull request y en
cada push a `main` (`.github/workflows/ci.yml`). Además levanta los ocho contenedores con Docker Compose y
recorre la demo con `scripts/verificar-demo.mjs` y corre la prueba de carga básica con
`scripts/prueba-carga.mjs`. Un PR con la verificación en rojo no se mergea.

Los dos scripts tienen sus propios tests, contra un sistema simulado y sin `npm install`:

```bash
node --test scripts/*.test.mjs
```

## Cómo levantar el backend

Copiar `.env.example` a `.env` en la raíz y ajustar las credenciales locales.
Compose lee ese archivo; Spring necesita las mismas variables en el entorno de la terminal.
En PowerShell, desde la raíz:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
Get-Content .env | ForEach-Object {
    if ($_ -match '^(DB_NAME|DB_USER|DB_PASSWORD|MYSQL_ROOT_PASSWORD|DB_PORT)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
```

Verificar que `JAVA_HOME` apunte a Java 21 antes de ejecutar Maven.

```bash
docker compose up -d mysql

cd backend
./mvnw install -DskipTests
./mvnw -pl reclamos-app spring-boot:run
```

La aplicación queda en `http://localhost:8080` con el perfil `dev`.

## Cómo levantar el frontend

Con el backend corriendo en el puerto 8080:

```bash
cd frontend
npm install
npm run dev
```

La interfaz queda en `http://localhost:5173` y empieza en la pantalla de ingreso. Los siete usuarios de la
semilla entran con su email (`ana@example.org`, `carla@example.org`, `elena@example.org`, etc.) y la
contraseña de desarrollo `demo1234`. Un vecino nuevo puede crear su cuenta desde la misma pantalla. La sesión
queda en una cookie del servidor; ver [Sesión](docs/api-contract.md#sesión) en el contrato.

| Rol | Qué puede hacer en la interfaz |
| --- | --- |
| Ciudadano | Verificar el teléfono, crear reclamos con foto y ubicación, apoyar un reclamo igual en vez de crear otro, ver los suyos, cancelar mientras nadie lo tomó, confirmar la solución o reabrir (una vez, dentro de 7 días) |
| Agente municipal | Ver los reclamos de su área, tomarlos y resolverlos |
| Administrador | Ver todos, asignar a mano o rechazar los que quedaron sin área, reasignar, y bloquear o desbloquear cuentas de ciudadanos |

Los tres roles tienen además la pestaña **Avisos**, con las notificaciones que recibió el usuario, y la
pestaña **Tablero**: el público para todos y, para agentes y administradores, también el de gestión. El
tablero público se ve sin iniciar sesión, desde la pantalla de ingreso.

El navegador llama a `/api` en el mismo origen y Vite reenvía esas llamadas al backend, por eso no
hace falta configurar CORS. Para apuntar a otro backend, copiar `frontend/.env.example` a
`frontend/.env` y cambiar `VITE_BACKEND_URL`.

Para trabajar en la interfaz sin Java ni MySQL hay una API simulada en memoria que sigue el
contrato REST, incluido el control de vencimientos. Usa el puerto 8080, así que no puede correr a
la vez que el backend:

```bash
cd frontend
npm run mock      # en una terminal
npm run dev       # en otra
```

Tests del frontend: `npm test`.

## Demo completa con Docker

Levanta el sistema completo en ocho contenedores: MySQL, backend, frontend, los servicios de cobertura
(SOAP), prioridad (IA) y notificaciones, RabbitMQ y Mailpit. Solo hace falta Docker y el archivo `.env`:

```bash
docker compose up --build
```

La interfaz queda en `http://localhost:5173` y la API en `http://localhost:8080/api`. La primera
vez tarda varios minutos porque compila el backend y los servicios dentro de los contenedores.

Para comprobar que todo anda de punta a punta, con la demo levantada y en otra terminal:

```bash
docker compose run --rm verificar-demo
```

Crea un reclamo de prueba y sigue su recorrido por Georef, la IA, el servicio SOAP, RabbitMQ y el correo en
Mailpit. La matriz de fallos está en [despliegue](docs/despliegue.md#matriz-de-fallos).

Para la prueba de carga básica (20 usuarios simultáneos leyendo durante 30 s, sin crear reclamos):

```bash
docker compose run --rm -T prueba-carga > docs/evidencias/prueba-carga.md
```

Las metas y lo que mide están en [Prueba de carga](docs/requerimientos.md#prueba-de-carga).

## Perfiles

| Perfil | Base de datos | Uso |
| --- | --- | --- |
| `dev` (por defecto) | MySQL en Docker, `localhost:3306`, base `reclamos` | Desarrollo y demo |
| `test` | H2 en memoria | Tests automáticos |

La conexión de `dev` se puede cambiar sin tocar archivos con las variables `DB_URL`, `DB_USER` y
`DB_PASSWORD`; también admite `DB_NAME` y `DB_PORT` cuando no se define `DB_URL`.

En `dev`, Hibernate actualiza el esquema y luego se ejecuta
`reclamos-persistencia/src/main/resources/db/datos-dev.sql`. La semilla respeta los IDs de
`docs/api-contract.md`: seis jurisdicciones (Quilmes, Avellaneda, Berazategui, Comuna 13, Comuna 14 y San Isidro),
ocho categorías, dieciocho áreas y ocho usuarios. Desde el PR 25 no hay barrios: una base creada antes se borra una vez con
`docker compose down -v`. La tabla técnica `inicializaciones` registra el marcador `datos-dev-v2` al terminar la primera
ejecución. Los siguientes arranques no restauran datos ni relaciones eliminadas o modificadas.
Esta tabla se usa únicamente para inicializar datos de desarrollo; una base nueva vuelve a cargar
la semilla. En una base existente sin marcador, la primera ejecución completa los datos faltantes.
Si ya hubo eliminaciones que deben conservarse, registrar antes el marcador con
`INSERT INTO inicializaciones (identificador) VALUES ('datos-dev-v1');`, creando previamente
la tabla con `CREATE TABLE IF NOT EXISTS inicializaciones (identificador VARCHAR(80) PRIMARY KEY);`.
El volumen de Compose conserva los datos; cambiar las variables no cambia las credenciales de un
volumen ya inicializado.

Para verificar la base después de arrancar el backend, desde la raíz:

```bash
docker compose exec mysql mysql -u reclamos -p reclamos
```

Si cambiaste `DB_USER` o `DB_NAME`, reemplazar esos argumentos. Ingresar la contraseña de `.env`.
Consultar `SELECT id, nombre FROM areas_municipales ORDER BY id;` y
`SELECT id, tipo, nombre, apellido FROM usuarios ORDER BY id;`.
Reiniciar el backend y repetir: deben conservarse los mismos cuatro y siete registros.

Los tests de persistencia usan H2, guardan y hacen flush/clear antes de recuperar datos.
También comprueban actualizaciones, consultas, semilla repetible y relaciones fuera de transacción.
El perfil `test` no carga la semilla de desarrollo automáticamente.

## Configuración de estrategias

Las estrategias activas se eligen en `backend/reclamos-app/src/main/resources/application.yml`:

```yaml
reclamos:
  prioridad:
    estrategia: categoria        # categoria | palabras-clave | ia
  asignacion:
    estrategia: jurisdiccion     # jurisdiccion | carga-trabajo
  cobertura:
    origen: local                # local (la base del backend) | soap (servicio-cobertura)
```

Son los valores por defecto, con el backend levantado a mano. En Compose se cambian por variables de
entorno: `RECLAMOS_PRIORIDAD_ESTRATEGIA: ia` y `RECLAMOS_COBERTURA_ORIGEN: soap`.

`categoria` conserva la prioridad base. `palabras-clave` busca las palabras completas
`urgente`, `peligro`, `riesgo` y `accidente` en la descripción, sin distinguir mayúsculas:
si encuentra alguna, devuelve el máximo entre la prioridad base y `ALTA`. No interpreta
negaciones, plurales ni contexto; varias coincidencias no acumulan aumentos. `ia` le pide la prioridad
al servicio de prioridad (REST 2, con Gemini) y se queda con la mayor entre esa y la base; si el servicio
falla o tarda más de 4 s, usa la base y, con la mensajería prendida, la IA lo revisa después por RabbitMQ
(hasta 4 intentos, cada 20 s). El detalle está en [Prioridad con IA](docs/arquitectura.md#prioridad-con-ia).

`cobertura.origen` elige de dónde salen las áreas candidatas: `local` las consulta en la base del
backend y `soap` se las pide a `servicio-cobertura`. Las dos dan el mismo resultado; ver
[Acceso local y remoto](docs/arquitectura.md#acceso-local-y-remoto-entre-componentes).

Las estrategias de asignación reciben únicamente las áreas candidatas de `CatalogoCobertura`
(activas, del municipio del reclamo y que atienden su categoría). `jurisdiccion` elige el menor ID;
`carga-trabajo` elige la menor cantidad de reclamos `ASIGNADO` o `EN_PROCESO`, con desempate
por menor ID. Sin candidatas, ambas devuelven vacío. Una estrategia configurada con un
nombre desconocido impide el arranque con un mensaje de configuración.

El reclamo tiene bloqueo optimista (`@Version`): si dos operaciones lo modifican a la vez, la segunda
recibe `409 CONFLICTO_CONCURRENTE` y tiene que volver a cargarlo.

## Documentación de la API (Swagger)

Con el backend levantado, Swagger UI muestra y permite probar todos los endpoints:
`http://localhost:8080/swagger-ui.html`. El contrato OpenAPI está en `/v3/api-docs.yaml` y versionado en
[`docs/openapi/reclamos.yaml`](docs/openapi/README.md), que `./mvnw test` regenera.

## Control de vencimientos

Una tarea programada busca cada minuto los reclamos que pasaron su fecha límite sin resolverse.
Los marca como vencidos, les sube un nivel de prioridad y publica el evento `ReclamoVencido`, que
avisa al ciudadano y al área.

```yaml
reclamos:
  vencimientos:
    habilitado: true       # false apaga la tarea; es el valor del perfil test
    intervalo-ms: 60000
```

Una clave desconocida dentro de `reclamos` (por ejemplo `vencimiento` en singular) o un
`intervalo-ms` que no sea mayor que cero impiden el arranque con un mensaje de configuración.

## Documentación

- [Requerimientos](docs/requerimientos.md): funcionales y no funcionales con su verificación, los del
  componente de IA (exactitud, latencia, privacidad), la prueba de carga y las decisiones de diseño de la IA.
- [Arquitectura](docs/arquitectura.md): capas, componentes con sus interfaces provistas y requeridas,
  acceso local y remoto entre componentes, servicios, integraciones y decisiones de diseño.
- [Modelo de dominio](docs/modelo-dominio.md): clases, reglas del dominio y tablas.
- [Patrones de diseño](docs/patrones.md): Factory, Repository, Strategy, Observer y Facade; Adapter,
  proxy remoto, proxy de caché, Decorator, circuit breaker, outbox y consumidor idempotente en las
  integraciones; Chain of Responsibility en el login y en los controles de uso; lado de lectura separado
  (Domain Reporting) para los tableros.
- [Proceso de negocio y secuencias](docs/secuencias.md): estados, proceso de negocio (con la
  verificación, los duplicados y la reapertura), creación, el alta con sus cinco integraciones, cambio de
  estado y vencimiento.
- [Contrato REST](docs/api-contract.md): endpoints, objetos, errores y datos semilla.
- [Contratos de integración](docs/contratos/README.md): SOAP, REST 2 y mensajes de la Segunda Parte, con el
  [flujo de mensajes](docs/contratos/mensajes.md#topología) de RabbitMQ.
- [Despliegue](docs/despliegue.md): contenedores, puertos, arranque, memoria, configuración y matriz de fallos.
- [Evidencias](docs/evidencias/README.md): cómo demostrar cada caso, capturas de la interfaz,
  [evaluación de la IA](docs/evidencias/ia-evaluacion.md) y [prueba de carga](docs/evidencias/prueba-carga.md).
