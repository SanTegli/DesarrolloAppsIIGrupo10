# Sistema de Reclamos Urbanos

TPO de Desarrollo de Aplicaciones II (UADE) — Grupo 10. Docente: Mg. Christian Parkinson.

Los ciudadanos reportan problemas en la vía pública (baches, luminarias rotas, residuos), el sistema
asigna cada reclamo al área municipal que corresponde y los agentes lo resuelven.

Este repositorio contiene el **Trabajo Práctico Primera Parte (Hito 1)**: aplicación empresarial en
capas con los patrones Factory, Repository, Strategy, Observer y Facade.

## Estructura

```text
backend/                    Proyecto Maven multimódulo (Java 21, Spring Boot 3)
  reclamos-comun/           Utilidades: excepciones de negocio y validación
  reclamos-dominio/         Modelo, reglas, fábrica, eventos y contratos. Java puro, sin Spring
  reclamos-persistencia/    Adaptadores JPA de los Repository
  reclamos-app/             Servicios, fachada, eventos y API REST
frontend/                   Interfaz React + Vite
  src/api/                  Única capa que habla con el backend
  src/componentes/          Pantallas: listado, alta, detalle y administración
  mock/                     API simulada para trabajar sin Java ni MySQL
docs/                       Contrato REST, arquitectura, patrones, secuencias y evidencias
docker-compose.yml          MySQL para desarrollo; backend y frontend para la demo
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

En Windows, `.\mvnw.cmd test`.

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

La interfaz queda en `http://localhost:5173`. No hay login: el selector "Usar el sistema como"
elige el usuario y la interfaz envía su id en el encabezado `X-Usuario-Id`.

| Rol | Qué puede hacer en la interfaz |
| --- | --- |
| Ciudadano | Crear reclamos, ver los suyos, cancelar, confirmar la solución o reabrir |
| Agente municipal | Ver los reclamos de su área, tomarlos y resolverlos |
| Administrador | Ver todos, asignar a mano, reasignar y rechazar |

El navegador llama a `/api` en el mismo origen y Vite reenvía esas llamadas al backend, por eso no
hace falta configurar CORS. Para apuntar a otro backend, copiar `frontend/.env.example` a
`frontend/.env` y cambiar `VITE_BACKEND_URL`.

Para trabajar en la interfaz sin Java ni MySQL hay una API simulada en memoria que sigue el
contrato REST. Usa el puerto 8080, así que no puede correr a la vez que el backend:

```bash
cd frontend
npm run mock      # en una terminal
npm run dev       # en otra
```

Tests del frontend: `npm test`.

## Demo completa con Docker

Levanta MySQL, backend y frontend en tres contenedores. Solo hace falta Docker y el archivo `.env`:

```bash
docker compose up --build
```

La interfaz queda en `http://localhost:5173` y la API en `http://localhost:8080/api`. La primera
vez tarda varios minutos porque compila el backend dentro del contenedor.

## Perfiles

| Perfil | Base de datos | Uso |
| --- | --- | --- |
| `dev` (por defecto) | MySQL en Docker, `localhost:3306`, base `reclamos` | Desarrollo y demo |
| `test` | H2 en memoria | Tests automáticos |

La conexión de `dev` se puede cambiar sin tocar archivos con las variables `DB_URL`, `DB_USER` y
`DB_PASSWORD`; también admite `DB_NAME` y `DB_PORT` cuando no se define `DB_URL`.

En `dev`, Hibernate actualiza el esquema y luego se ejecuta
`reclamos-persistencia/src/main/resources/db/datos-dev.sql`. La semilla respeta los IDs de
`docs/api-contract.md`: un municipio, tres barrios, tres categorías, cuatro áreas y siete usuarios.
La tabla técnica `inicializaciones` registra el marcador `datos-dev-v1` al terminar la primera
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
    estrategia: categoria        # categoria | palabras-clave
  asignacion:
    estrategia: jurisdiccion     # jurisdiccion | carga-trabajo
```

`categoria` conserva la prioridad base. `palabras-clave` busca las palabras completas
`urgente`, `peligro`, `riesgo` y `accidente` en la descripción, sin distinguir mayúsculas:
si encuentra alguna, devuelve el máximo entre la prioridad base y `ALTA`. No interpreta
negaciones, plurales ni contexto; varias coincidencias no acumulan aumentos.

Las estrategias de asignación reciben únicamente las áreas candidatas del Repository
(activas, que atienden la categoría y cubren el barrio). `jurisdiccion` elige el menor ID;
`carga-trabajo` elige la menor cantidad de reclamos `ASIGNADO` o `EN_PROCESO`, con desempate
por menor ID. Sin candidatas, ambas devuelven vacío. Una estrategia configurada con un
nombre desconocido impide el arranque con un mensaje de configuración.

El PR3 no incorpora bloqueos ni un campo de versión. Como mejora futura, evaluar bloqueo
optimista para evitar que operaciones simultáneas sobrescriban el mismo reclamo.
Tampoco incorpora un scheduler de vencimientos.

## Documentación

- [Arquitectura](docs/arquitectura.md): capas, componentes, servicios y decisiones de diseño.
- [Patrones de diseño](docs/patrones.md): Factory, Repository, Strategy, Observer y Facade.
- [Proceso de negocio y secuencias](docs/secuencias.md): estados, proceso, creación y cambio de estado.
- [Contrato REST](docs/api-contract.md): endpoints, objetos, errores y datos semilla.
- [Evidencias](docs/evidencias/README.md): cómo demostrar cada caso y capturas de la interfaz.
- [Instructivo para Windows](README_INSTRUCTIVO.md): instalación y ejecución paso a paso.
- [Resumen técnico de PR 1 a 3](README_PR3.md).
- [Entrega de la versión inicial](docs/referencia/entrega-version-inicial.md): documento de la
  primera versión del TP, conservado como referencia.

## Plan de trabajo del Hito 1

| PR | Rama | Contenido |
| --- | --- | --- |
| 1 | `feature/01-domain-foundation` | Estructura, dominio, reglas, Factory y contratos |
| 2 | `feature/02-persistence-repositories` | Persistencia JPA, MySQL y datos semilla |
| 3 | `feature/03-services-rest` | Servicios, Strategy, Observer, Facade y API REST |
| 4 | `feature/04-frontend-integration` | React, integración, documentación y Docker |
