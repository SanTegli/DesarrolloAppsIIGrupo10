# Guía para levantar Sistema de Reclamos Urbanos desde cero

Guía paso a paso para levantar el sistema completo en Windows: MySQL, backend y frontend. La arquitectura y los patrones están en [docs/](docs/arquitectura.md).

Los comandos están pensados para PowerShell en Windows. Ejecutarlos en orden y respetar el directorio indicado. MySQL corre en Docker; Spring Boot y el frontend corren en Windows.

## 1. Qué se necesita instalar

| Herramienta | Para qué se usa |
| --- | --- |
| Git | Clonar y consultar versiones del repositorio. |
| Java JDK 21 | Compilar y ejecutar el backend. Se necesita JDK, que incluye `javac`. |
| Docker Desktop | Ejecutar MySQL; debe estar iniciado y usar contenedores Linux. |
| Docker Compose | Administrar el servicio definido en `docker-compose.yml`; normalmente viene integrado en Docker Desktop. |
| PowerShell | Ejecutar los comandos de esta guía y cargar variables en la sesión. |
| Maven Wrapper incluido | Ejecutar Maven mediante `backend/mvnw.cmd`. No debería ser necesario instalar Maven globalmente. |

El POM padre declara Java **21** y Spring Boot **3.5.9**. El Wrapper configura Maven **3.9.16** en `backend/.mvn/wrapper/maven-wrapper.properties`; la primera ejecución puede descargar Maven y dependencias, por lo que necesita conexión a Internet.

Abrir PowerShell y comprobar:

```powershell
java -version
javac -version
docker --version
docker compose version
git --version
echo $env:JAVA_HOME
where.exe java
```

`java` y `javac` deberían corresponder a Java 21. `JAVA_HOME`, si está definido, debe señalar la carpeta del JDK 21, sin agregar `bin`. `where.exe java` permite detectar otras instalaciones que aparecen en `PATH`. Si se cambia la configuración de Windows, abrir una terminal nueva. La ruta del JDK depende de cada instalación: no copiar una ruta de otra PC.

## 2. Clonar el repositorio

Desde la carpeta donde se quiera guardar el proyecto:

```powershell
git clone <URL_DEL_REPOSITORIO>
cd DesarrolloAppsIIGrupo10
git branch --show-current
git status --short
```

Reemplazar `<URL_DEL_REPOSITORIO>` por la URL correspondiente. El remoto `origin` verificado en esta copia es `https://github.com/SanTegli/DesarrolloAppsIIGrupo10.git`.

Trabajar sobre la rama `main` actualizada (`git pull`): contiene el backend completo y el frontend.

## 3. Estructura general del proyecto

```text
DesarrolloAppsIIGrupo10/
  backend/
    pom.xml
    mvnw.cmd
    .mvn/wrapper/maven-wrapper.properties
    reclamos-comun/
    reclamos-dominio/
    reclamos-persistencia/
    reclamos-app/
  frontend/
    package.json
    src/
  docs/
    api-contract.md
    arquitectura.md
    patrones.md
    secuencias.md
    despliegue.md
    evidencias/
  docker-compose.yml
  .env.example
```

| Módulo | Responsabilidad |
| --- | --- |
| `reclamos-comun` | Validación reutilizable y excepciones. |
| `reclamos-dominio` | Modelo, reglas, Factory, eventos e interfaces Repository y Strategy; Java sin Spring ni JPA. |
| `reclamos-persistencia` | Mappings XML, repositorios Spring Data y adaptadores JPA. |
| `reclamos-app` | Inicio de Spring Boot, perfiles, servicios, Facade, Strategies, Observer y REST. |

`docs/api-contract.md` contiene el contrato REST. `frontend/` es la interfaz React, que se levanta después del backend (sección 8).

## 4. Configurar variables de entorno

Desde la raíz, copiar la plantilla **si todavía no existe un `.env` propio**:

```powershell
Copy-Item .env.example .env
```

Editar `.env` en un editor de texto y definir las credenciales locales antes de inicializar MySQL. La plantilla contiene valores ficticios de desarrollo; no usar credenciales de producción.

| Variable real de `.env.example` | Valor o propósito |
| --- | --- |
| `DB_NAME` | Nombre de la base; plantilla: `reclamos`. |
| `DB_USER` | Usuario de la aplicación; plantilla: `reclamos`. |
| `DB_PASSWORD` | Contraseña local del usuario de aplicación. Obligatoria para Compose y Spring en `dev`. |
| `MYSQL_ROOT_PASSWORD` | Contraseña local del administrador MySQL. Compose la requiere; Spring no la usa para conectarse. |
| `DB_PORT` | Puerto publicado en Windows; plantilla: `3306`. |

**`.env` no debe subirse a Git** y está excluido por `.gitignore`. Docker Compose carga automáticamente el `.env` de la raíz. Spring Boot ejecutado directamente desde PowerShell **no necesariamente lee `.env`**; este proyecto obtiene las variables desde el entorno del proceso.

Desde la raíz, cargar el archivo en la sesión actual:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable(
            $matches[1].Trim(),
            $matches[2].Trim(),
            'Process'
        )
    }
}
```

Usar en este archivo el formato simple `NOMBRE=valor`, sin comillas ni comentarios al final del valor: el bloque no interpreta toda la sintaxis de Compose. Ejecutarlo en **la terminal desde la cual luego se iniciará Spring Boot**. Una terminal nueva no hereda estas variables de proceso. Si se edita `.env`, volver a cargarlo.

`application-dev.yml` también admite `DB_URL` como reemplazo completo de la URL JDBC, aunque **no forma parte de `.env.example`**. Si ya está definida en la sesión, prevalece sobre `DB_PORT` y `DB_NAME` para Spring. Para esta guía se utiliza la URL predeterminada hacia `localhost`.

## 5. Puerto de MySQL

Compose publica `${DB_PORT:-3306}:3306`:

| Lugar | Puerto |
| --- | --- |
| Dentro del contenedor MySQL | Siempre `3306` según Compose. |
| Windows, donde se ejecuta Spring | `DB_PORT`; por defecto `3306`. |

Spring construye su URL hacia `localhost:${DB_PORT:3306}`. El puerto publicado debe coincidir con el que usa Spring, salvo que se configure explícitamente `DB_URL`.

Si el puerto 3306 ya está ocupado por un MySQL local, comprobar:

```powershell
Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue
```

Como alternativa, cambiar **en `.env`** `DB_PORT=3307`, volver a cargar las variables y ejecutar `docker compose up -d mysql` para aplicar la publicación. Dentro del contenedor continúa siendo 3306; Windows y Spring usarán 3307. No es obligatorio cambiarlo si 3306 está libre. Una variable `DB_PORT` ya definida en PowerShell puede prevalecer sobre `.env` para Compose: mantener ambos valores sincronizados.

## 6. Levantar MySQL con Docker

Iniciar Docker Desktop y, desde la raíz:

```powershell
docker compose up -d mysql
docker compose ps
```

El servicio `mysql`, contenedor `reclamos-mysql`, usa la imagen `mysql:8.4` y el volumen `reclamos-mysql-data`. La primera ejecución puede descargar la imagen e inicializar la base. Esperar a que el estado indique **`healthy`**; mientras diga `starting`, repetir `docker compose ps`.

Diagnóstico:

```powershell
docker compose logs mysql
```

El volumen conserva la base entre reinicios. Estos comandos tienen efectos distintos:

```powershell
docker compose stop
docker compose down
```

`stop` detiene los contenedores y los conserva. `down` elimina contenedores y la red creada por Compose, pero conserva el volumen nombrado.

**Advertencia: `docker compose down -v` elimina el volumen y los datos de MySQL. No ejecutarlo accidentalmente ni usarlo como primera solución a un error de conexión.**

## 7. Ejecutar todos los tests

Desde la raíz:

```powershell
cd backend
.\mvnw.cmd test
```

El resultado esperado es **`BUILD SUCCESS`** y módulos sin fallos. No se necesita MySQL ni Docker para los tests.

La suite incluye validación común; dominio, transiciones y Factory; persistencia y relaciones JPA; Strategies; servicios, consultas y permisos; Observer y rollback; Facade; y REST mediante MockMvc con componentes reales e integración H2.

Los 27 reportes XML existentes en `backend/*/target/surefire-reports/TEST-*.xml`, fechados el 03/10/2026, suman **221 tests**, con 0 fallos, 0 errores y 0 omitidos. Es evidencia de la ejecución registrada, no de una nueva ejecución realizada para redactar esta guía. Los casos parametrizados cuentan por ejecución.

H2 en memoria se usa para pruebas, en modo MySQL y con `ddl-auto: create-drop`; el perfil `dev` usa MySQL real. La semilla de desarrollo no se carga automáticamente con `test`; las pruebas preparan sus escenarios y `DatosSemillaTest` ejecuta el SQL de manera explícita.

## 8. Levantar Spring Boot contra MySQL

Antes de continuar: MySQL debe estar `healthy`, `.env` debe estar cargado en **esta sesión** y Java 21 debe estar activo.

Desde `backend`, instalar primero los artefactos del reactor en el repositorio Maven local:

```powershell
.\mvnw.cmd install -DskipTests
```

Este paso es necesario en una PC nueva: `test` compila y prueba, pero no instala los módulos hermanos. El comando con `-pl reclamos-app` necesita resolver `reclamos-persistencia`, `reclamos-dominio`, `reclamos-comun` y el POM padre. `-DskipTests` evita repetir la suite que se acaba de ejecutar; esperar `BUILD SUCCESS`.

Luego, desde el mismo `backend`:

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
.\mvnw.cmd -pl reclamos-app spring-boot:run
```

`dev` ya es el perfil predeterminado en `application.yml`; fijarlo explícitamente evita iniciar con un perfil heredado de otra tarea. Mantener esta terminal abierta. Para probar la API, usar otra terminal.

Buscar mensajes que indiquen el perfil `dev` activo, la conexión de HikariPool, MySQL 8.4.x según la imagen configurada, `Tomcat started on port 8080` y `Started ReclamosApplication`. El texto y la versión de parche pueden variar; estos mensajes son señales esperadas, no logs de un arranque verificado durante esta documentación.

La API se sirve en `http://localhost:8080/api`. Si 8080 está ocupado, identificar el proceso y liberar el puerto antes de seguir estos ejemplos.

### Levantar el frontend

Requiere Node.js 22 (o 20.19 en adelante). Con el backend ya iniciado, en **otra terminal**, desde la raíz del repositorio:

```powershell
cd frontend
npm install
npm run dev
```

Abrir `http://localhost:5173`. El selector "Usar el sistema como" elige el usuario de la semilla; no hay login. Si la página muestra "El servidor no responde", el backend no está escuchando en 8080.

## 9. Datos semilla

Después de que Hibernate actualiza el esquema (`ddl-auto: update`), Spring ejecuta `backend/reclamos-persistencia/src/main/resources/db/datos-dev.sql`, en UTF-8. Compose crea la base y usuarios MySQL, pero la semilla del dominio se carga al iniciar Spring.

Se crea el municipio **Quilmes**, provincia **Buenos Aires**, con estos datos ficticios:

| ID de barrio | Nombre |
| --- | --- |
| 1 | Quilmes Centro |
| 2 | Bernal |
| 3 | Ezpeleta |

| ID de categoría | Nombre | SLA (horas) | Prioridad base |
| --- | --- | --- | --- |
| 1 | Luminaria rota | 48 | MEDIA |
| 2 | Bache | 120 | BAJA |
| 3 | Residuos | 72 | MEDIA |

| ID de área | Nombre | Categoría atendida | Barrios cubiertos |
| --- | --- | --- | --- |
| 1 | Alumbrado | Luminaria rota | Quilmes Centro, Bernal |
| 2 | Obras Públicas | Bache | Quilmes Centro, Bernal, Ezpeleta |
| 3 | Higiene Urbana | Residuos | Quilmes Centro, Bernal |
| 4 | Mantenimiento Vial | Bache | Bernal |

Las cuatro áreas están activas. Las relaciones se guardan en `area_barrio` y `area_categoria`.

| ID de usuario | Nombre | Rol | Área |
| --- | --- | --- | --- |
| 1 | Ana Pérez | CIUDADANO | — |
| 2 | Bruno Díaz | CIUDADANO | — |
| 3 | Carla Gómez | AGENTE_MUNICIPAL | Alumbrado |
| 4 | Diego Sosa | AGENTE_MUNICIPAL | Obras Públicas |
| 5 | Fabián Luna | AGENTE_MUNICIPAL | Higiene Urbana |
| 6 | Gabriela Paz | AGENTE_MUNICIPAL | Mantenimiento Vial |
| 7 | Elena Ruiz | ADMINISTRADOR | — |

No hay reclamos ni notificaciones precargados. La tabla técnica `inicializaciones` registra el identificador **`datos-dev-v1`** al completar la semilla. El script se invoca en cada arranque de `dev`, pero sus inserciones quedan condicionadas a la ausencia del marcador: la semilla se aplica **una sola vez por base** y las modificaciones posteriores no se restauran en cada reinicio.

En una base existente sin marcador se completan registros y relaciones faltantes sin sobrescribir IDs existentes. Una base nueva vuelve a cargar la semilla. Los ejemplos siguientes presuponen una base recién inicializada, sin cambios manuales en sus IDs o jurisdicciones.

## 10. Probar la API

Abrir otra PowerShell mientras Spring sigue ejecutándose:

```powershell
Invoke-RestMethod http://localhost:8080/api/usuarios
Invoke-RestMethod http://localhost:8080/api/categorias
Invoke-RestMethod http://localhost:8080/api/barrios
Invoke-RestMethod http://localhost:8080/api/areas
```

Estas consultas no requieren identidad. Para comprobar expresamente el HTTP 200:

```powershell
(Invoke-WebRequest -Uri "http://localhost:8080/api/usuarios" -UseBasicParsing).StatusCode
```

Todos los endpoints de reclamos requieren **`X-Usuario-Id`**, un entero positivo que corresponde a un usuario existente. Simula al usuario autenticado del Hito 1; todavía no hay login ni JWT real.

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos" `
    -Headers @{"X-Usuario-Id"="1"}
```

Devuelve los reclamos del ciudadano 1; inicialmente puede devolver `[]`.

### Crear y consultar un reclamo

`CrearReclamoRequest` tiene exactamente cuatro campos: `categoriaId`, `barrioId`, `descripcion` y `direccion`. Los IDs deben ser positivos y existir; descripción y dirección deben tener texto, con máximos de 1000 y 200 caracteres respectivamente. El ciudadano se obtiene del encabezado.

```powershell
$body = @{
    categoriaId = 1
    barrioId = 2
    descripcion = "Farol apagado frente a la plaza"
    direccion = "Belgrano 450"
} | ConvertTo-Json

$creado = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos" `
    -Method Post `
    -Headers @{"X-Usuario-Id"="1"} `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

$creado | ConvertTo-Json -Depth 10
$numero = $creado.numero

Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos/$numero" `
    -Headers @{"X-Usuario-Id"="1"} | ConvertTo-Json -Depth 10

Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos/$numero/notificaciones" `
    -Headers @{"X-Usuario-Id"="1"} | ConvertTo-Json -Depth 10
```

El POST responde **201 Created**, con `Location: /api/reclamos/{numero}`. Con la semilla y las Strategies predeterminadas, queda `ASIGNADO` a Alumbrado, prioridad `MEDIA`, agente `null`, dos registros de historial y dos notificaciones `INTERNO` para el ciudadano: ingreso y asignación. El número real es generado; reutilizar `$numero`, sin copiar uno ficticio.

Para probar falta de cobertura, usar categoría 1 y barrio 3: queda `INGRESADO`, `area: null`, un historial y una notificación de ingreso; también responde 201. El administrador puede asignarlo manualmente.

### Probar toma, resolución y cierre

Sobre el ejemplo asignado a Alumbrado, el usuario 3 puede tomarlo y resolverlo; el ciudadano 1 puede cerrar o reabrir cuando está resuelto:

```powershell
$bodyEstado = @{ estado = "EN_PROCESO"; observacion = "Cuadrilla en camino" } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos/$numero/estado" `
    -Method Patch -Headers @{"X-Usuario-Id"="3"} `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($bodyEstado))

$bodyEstado = @{ estado = "RESUELTO"; observacion = "Luminaria reparada" } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos/$numero/estado" `
    -Method Patch -Headers @{"X-Usuario-Id"="3"} `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($bodyEstado))

$bodyEstado = @{ estado = "CERRADO"; observacion = "Reparacion confirmada" } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos/$numero/estado" `
    -Method Patch -Headers @{"X-Usuario-Id"="1"} `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($bodyEstado))
```

Cada PATCH exitoso responde 200. El detalle incluye `historial` y `accionesDisponibles` según el actor; el endpoint de notificaciones devuelve todos los avisos del reclamo visible, incluidos los destinados al agente.

## 11. Endpoints disponibles

| Método | Ruta | Uso |
| --- | --- | --- |
| POST | `/api/reclamos` | Crear como ciudadano. |
| GET | `/api/reclamos` | Listar según rol; filtro `estado` para todos, `areaId` y `ciudadanoId` solo administrador. |
| GET | `/api/reclamos/{numero}` | Ver detalle, historial y acciones permitidas. |
| PATCH | `/api/reclamos/{numero}/estado` | Cambiar estado; tomar usa `EN_PROCESO`. |
| PATCH | `/api/reclamos/{numero}/asignacion` | Asignar o reasignar como administrador. |
| GET | `/api/reclamos/{numero}/notificaciones` | Consultar avisos del reclamo con los mismos permisos del detalle. |
| GET | `/api/usuarios` | Listar usuarios del selector de identidad. |
| GET | `/api/usuarios/{id}` | Consultar usuario por ID positivo. |
| GET | `/api/categorias` | Listar categorías, SLA y prioridad base. |
| GET | `/api/barrios` | Listar barrios y nombre de municipio. |
| GET | `/api/areas` | Listar áreas, estado activo y cobertura. |

`CambiarEstadoRequest` admite `estado` obligatorio y `observacion` opcional de hasta 1000 caracteres. `ASIGNADO` debe solicitarse por asignación, nunca por `/estado`. `AsignarReclamoRequest` admite `areaId` obligatorio y positivo, y `observacion` opcional de hasta 1000 caracteres. La asignación manual requiere un área activa y distinta de la actual, y un reclamo `INGRESADO` o `ASIGNADO`; no exige cobertura territorial ni categoría compatibles como la automática.

## 12. Códigos HTTP y errores

| HTTP | Código | Cuándo aparece |
| --- | --- | --- |
| 400 | `DATOS_INVALIDOS` | Falta encabezado/campo, IDs no positivos, JSON o enum inválido, longitud excesiva o intento de `ASIGNADO` por `/estado`. |
| 403 | `ACCESO_DENEGADO` | Rol no habilitado, reclamo de otro ciudadano/área o filtros administrativos sin permiso. |
| 404 | `RECURSO_NO_ENCONTRADO` | Usuario, reclamo, categoría, barrio, área o ruta inexistente. |
| 409 | `REGLA_NEGOCIO` | Transición inexistente, ciudadano inactivo al crear, área inactiva o igual a la actual al asignar. |
| 405 | `METODO_NO_PERMITIDO` | Método HTTP no admitido por el recurso. |
| 415 | `TIPO_CONTENIDO_NO_SOPORTADO` | Tipo de contenido no soportado; enviar JSON en las escrituras. |
| 500 | `ERROR_INTERNO` | Fallo inesperado; revisar los logs del backend. |

`ErrorResponse` contiene `status`, `codigo`, `mensaje`, `detalles`, `ruta` y `fecha`. Las validaciones pueden incluir un detalle por campo; otros errores normalmente devuelven `detalles: []`. Al cambiar estados se verifica primero que la transición exista: puede prevalecer 409 sobre 403. Un filtro administrativo con ID inexistente devuelve un listado vacío, no necesariamente 404.

## 13. Problemas frecuentes

### A. Access denied for user 'reclamos'@'localhost'

Comprobar usuario, base, puerto y contraseña de `.env`, sin publicar contraseñas. Volver a ejecutar el bloque de carga desde la raíz en **la misma terminal que inicia Spring**. Confirmar que `DB_URL` no redirija a otro servidor y que no se esté conectando al MySQL local por error.

MySQL inicializa credenciales al crear su directorio de datos. Cambiar `.env` no cambia la contraseña guardada en un volumen existente. Si no coinciden, usar las credenciales con las que se creó el volumen o actualizar el usuario mediante una cuenta administradora válida. No borrar el volumen como primera opción.

### B. Puerto 3306 ocupado

Usar `Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue`. Si se conserva el MySQL local, elegir otro puerto libre para `DB_PORT`, por ejemplo 3307, recargar `.env` y aplicar `docker compose up -d mysql` desde la raíz. El puerto interno sigue siendo 3306.

### C. JAVA_HOME / Java incorrecto

Desde `backend`:

```powershell
.\mvnw.cmd -version
```

Verificar que Maven muestre Java 21. Comparar con `java -version`, `javac -version`, `echo $env:JAVA_HOME` y `where.exe java`. Corregir `JAVA_HOME` y `PATH` hacia el JDK instalado y abrir una terminal nueva; después recargar `.env`.

### D. Spring no toma las variables de .env

Compose interpreta `.env`; el proceso Java local recibe el entorno de PowerShell. Si falta `DB_PASSWORD` o se usa un valor anterior, cargar nuevamente el archivo en esa sesión. Si la terminal está en `backend`, volver primero a la raíz con `cd ..`, cargarlo y luego regresar con `cd backend`.

### E. BUILD FAILURE

Leer la primera causa real y las líneas `Caused by`, no solo el resumen final de Maven. Distinguir fallo de compilación, test, descarga o arranque/conexión. Los reportes de tests quedan en `target/surefire-reports` dentro de cada módulo. Si al ejecutar `-pl reclamos-app` faltan artefactos `0.1.0-SNAPSHOT`, ejecutar antes `install -DskipTests` desde `backend`. Comprobar también Internet en la primera descarga y que la copia contenga los archivos de PR3.

### F. Caracteres como PÃ©rez en PowerShell

Puede ser un problema de decodificación o presentación de la consola, sin implicar datos corruptos. El SQL semilla es UTF-8 y Compose configura MySQL con `utf8mb4`. Revisar el encoding del editor y la terminal; se puede probar PowerShell 7 o comparar la respuesta con otro cliente que decodifique UTF-8. Los ejemplos POST envían bytes UTF-8 explícitos. No modificar datos solo por cómo se ven en una consola.

### G. Docker Desktop no inicia

Comprobar que Docker Desktop esté abierto, use contenedores Linux y que su motor esté listo. Según la instalación de Windows, revisar virtualización habilitada y los requisitos del backend WSL2 de Docker Desktop. No se verificó la configuración de cada PC; este es diagnóstico general. No reinicializar ni eliminar distribuciones o volúmenes para resolverlo sin revisar la causa.

## 14. Cómo apagar todo

En la terminal del backend y en la del frontend: **Ctrl+C**.

En otra terminal, desde la raíz del repositorio:

```powershell
docker compose stop
```

Para quitar contenedores y red conservando la base:

```powershell
docker compose down
```

`stop` permite volver a iniciar los contenedores; `down` permite recrearlos con `up -d mysql`. Ambos conservan el volumen nombrado. **`down -v` elimina los datos y requiere una decisión explícita de descartarlos.**

## 15. Checklist final

- [ ] La copia está en `main` actualizada.
- [ ] Java 21 activo, también en `.\mvnw.cmd -version`.
- [ ] Docker MySQL aparece `healthy`.
- [ ] Variables cargadas en la terminal que inicia Spring.
- [ ] Tests finalizados con `BUILD SUCCESS`.
- [ ] Artefactos Maven instalados para ejecutar `reclamos-app`.
- [ ] Spring iniciado con perfil `dev` en 8080.
- [ ] `/api/usuarios` responde 200.
- [ ] `/api/reclamos` responde con `X-Usuario-Id`.
- [ ] El frontend abre en `http://localhost:5173` y lista los usuarios en el selector.
