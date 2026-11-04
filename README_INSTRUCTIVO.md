# Guía para levantar Sistema de Reclamos Urbanos desde cero

Guía paso a paso para levantar el sistema completo en Windows: MySQL, backend y frontend. La arquitectura y los patrones están en [docs/](docs/arquitectura.md).

Los comandos están pensados para PowerShell en Windows. Ejecutarlos en orden y respetar el directorio indicado. MySQL corre en Docker; Spring Boot y el frontend corren en Windows.

## 1. Qué se necesita instalar

| Herramienta | Para qué se usa |
| --- | --- |
| Git | Clonar y consultar versiones del repositorio. |
| Java JDK 21 | Compilar y ejecutar el backend. Se necesita JDK, que incluye `javac`. |
| Docker Desktop | Ejecutar MySQL, o el sistema completo en modo demo (sección 8); debe estar iniciado y usar contenedores Linux. |
| Node.js 22 (o 20.19 en adelante) | Ejecutar el frontend. No hace falta para el modo demo con contenedores. |
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
    modelo-dominio.md
    patrones.md
    secuencias.md
    despliegue.md
    contratos/
    openapi/
    evidencias/
  servicios/
    pom.xml
    mvnw.cmd
    servicio-cobertura/
    servicio-prioridad/
    servicio-notificaciones/
  docker-compose.yml
  .env.example
```

| Módulo | Responsabilidad |
| --- | --- |
| `reclamos-comun` | Validación reutilizable y excepciones. |
| `reclamos-dominio` | Modelo, reglas, Factory, eventos e interfaces Repository y Strategy; Java sin Spring ni JPA. |
| `reclamos-persistencia` | Mappings XML, repositorios Spring Data y adaptadores JPA. |
| `reclamos-app` | Inicio de Spring Boot, perfiles, servicios, Facade, Strategies, Observer y REST. |
| `servicios/servicio-cobertura` | Servicio SOAP de cobertura, aparte del backend: otro proyecto Maven, otro proceso, puerto 8081. |
| `servicios/servicio-prioridad` | Servicio REST 2 de prioridad con IA (Gemini), también aparte, puerto 8082. |
| `servicios/servicio-notificaciones` | Consumidor de RabbitMQ: manda por correo los avisos que publica el backend. No tiene puerto propio. |

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
| `PRIORIDAD_IA_PROVEEDOR` | Proveedor del servicio de prioridad: `gemini` (plantilla), `openai` o `simulado`. |
| `GEMINI_API_KEY` | Clave gratuita de Google AI Studio, vacía en la plantilla. Ver «Probar el servicio de prioridad con IA», en la sección 10. |
| `OPENAI_API_KEY` | Solo si se usa `openai`; vacía en la plantilla. |
| `RABBITMQ_USER` | Usuario de RabbitMQ en el modo demo; plantilla: `reclamos`. |
| `RABBITMQ_PASSWORD` | Contraseña local de RabbitMQ; plantilla: `reclamos_dev`. Si falta en un `.env` viejo, Compose usa ese mismo valor. |
| `RECLAMOS_TELEGRAM_MODO` | Verificación del teléfono: `simulado` (plantilla, sin bot) o `telegram`. Ver «Probar la verificación del teléfono», en la sección 10. |
| `TELEGRAM_BOT_TOKEN` | Token del bot que da @BotFather, vacío en la plantilla. Solo con el modo `telegram`. **Nunca se commitea.** |
| `TELEGRAM_BOT_USUARIO` | Usuario del bot sin `@` (por ejemplo, `ReclamosQuilmesBot`). Solo con el modo `telegram`. |

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

GitHub Actions corre la misma suite en cada pull request; el resultado se ve en la pestaña **Checks** del PR. Los reportes de cada ejecución local quedan en `backend/*/target/surefire-reports/`.

Al terminar, `OpenApiTest` deja actualizado `docs/openapi/reclamos.yaml`, el contrato OpenAPI de la API. Si cambió, se commitea junto con el código.

Los servicios de integración se prueban aparte, desde la raíz:

```powershell
cd servicios
.\mvnw.cmd verify
```

`ServicioSoapHttpTest` levanta el servicio SOAP en un puerto libre y deja actualizado
`docs/contratos/soap/cobertura.wsdl`; `OpenApiPrioridadTest` hace lo mismo con
`docs/openapi/prioridad.yaml`. Si cambiaron, también se commitean. Los tests nunca llaman a Gemini: simulan
su API, así que no necesitan clave.

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

Abrir `http://localhost:5173`. Aparece la pantalla de ingreso: entrar con el email de un usuario de la semilla (sección 9), por ejemplo `ana@example.org`, y la contraseña `demo1234`. **Salir** cierra la sesión. Si la página muestra "El servidor no responde", el backend no está escuchando en 8080.

### Alternativa: todo en contenedores (modo demo)

En lugar de levantar Spring Boot y el frontend a mano, Docker Compose puede levantar los ocho contenedores: MySQL, backend, frontend, el servicio SOAP de cobertura, el servicio de prioridad con IA, RabbitMQ, el servicio de notificaciones y Mailpit. Solo necesita Docker Desktop y el archivo `.env` de la sección 4; no hace falta Java ni Node.js en la máquina.

Antes de empezar, detener el backend, el frontend y el servicio de cobertura locales si están corriendo: el modo demo usa los mismos puertos, 8080, 5173, 8081 y 8082, y además 5672, 15672, 1025 y 8025.

Desde la raíz del repositorio:

```powershell
docker compose up --build
```

La primera vez tarda varios minutos, porque compila el backend y los servicios dentro de los contenedores; las siguientes, las dependencias de Maven salen de una caché. Los contenedores arrancan en orden: el backend espera a MySQL, RabbitMQ y los servicios de cobertura y prioridad, y el frontend espera al backend. Cuando aparece `reclamos-frontend` en el log, abrir `http://localhost:5173`. La API queda en `http://localhost:8080/api`.

Docker Desktop necesita unos 4 GB de memoria asignada para los ocho contenedores (se ve y se cambia en **Settings → Resources**; con WSL 2, en el archivo `.wslconfig`). Cada servicio Java tiene un tope de memoria en `docker-compose.yml`.

| Contenedor | Qué corre | Puerto |
| --- | --- | --- |
| `reclamos-mysql` | MySQL 8.4 | 3306, o el valor de `DB_PORT` |
| `reclamos-backend` | Spring Boot con el perfil `dev` | 8080 |
| `reclamos-frontend` | nginx con la interfaz compilada; reenvía `/api` al backend | 5173 |
| `servicio-cobertura` | Servicio SOAP de cobertura; el backend le consulta las áreas de cada reclamo nuevo | 8081 |
| `servicio-prioridad` | Servicio de prioridad con IA; el backend le pide la prioridad de cada reclamo nuevo | 8082 |
| `reclamos-rabbitmq` | RabbitMQ; el backend publica cada aviso en el exchange `reclamos.eventos` | 5672, y la consola en 15672 |
| `servicio-notificaciones` | Consume los avisos y los manda por correo | No publica puertos |
| `reclamos-mailpit` | Servidor de correo de prueba: recibe los correos y los muestra en el navegador, sin mandar nada afuera | 1025 (SMTP) y 8025 (web) |

Para ver el estado y los logs, en otra terminal:

```powershell
docker compose ps
docker compose logs -f backend
```

En este modo el backend arranca con `RECLAMOS_COBERTURA_ORIGEN=soap`, `RECLAMOS_PRIORIDAD_ESTRATEGIA=ia` y `RECLAMOS_MENSAJERIA_HABILITADA=true`: la asignación automática pasa por el servicio SOAP, la prioridad por el servicio de IA y cada aviso sale por correo a través de RabbitMQ. El WSDL queda en `http://localhost:8081/ws/cobertura.wsdl` y Swagger del servicio de IA en `http://localhost:8082/swagger-ui.html`.

Para que la IA funcione, `.env` tiene que tener `GEMINI_API_KEY` (ver «Probar el servicio de prioridad con IA», en la sección 10). Sin clave todo arranca igual, pero cada reclamo sale con la prioridad de la categoría y origen `RESPALDO`.

**Comprobar que todo anda.** Con la demo levantada, en otra terminal desde la raíz:

```powershell
docker compose run --rm verificar-demo
```

Crea un reclamo de prueba como Ana Pérez y sigue su recorrido: sugerencias de Georef, prioridad con IA, área por SOAP, mensaje por RabbitMQ y correo en Mailpit. Muestra una línea por paso con `OK`, `AVISO` o `FALLA` y termina con `N pasos bien, N avisos, N fallas`. Un `AVISO` en Georef significa que no hay Internet: el resto sigue. Una `FALLA` en «Prioridad con IA» casi siempre es la clave de Gemini. El guion para presentar la demo, con las fallas que se pueden provocar a propósito, está en `docs/demo.md`.

Después de cambiar código, volver a ejecutar `docker compose up --build` para reconstruir las imágenes. Los datos de MySQL se conservan en el volumen `reclamos-mysql-data`, que es el mismo del modo desarrollo.

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

| ID de usuario | Nombre | Email para entrar | Rol | Área |
| --- | --- | --- | --- | --- |
| 1 | Ana Pérez | `ana@example.org` | CIUDADANO | — |
| 2 | Bruno Díaz | `bruno@example.org` | CIUDADANO | — |
| 3 | Carla Gómez | `carla@example.org` | AGENTE_MUNICIPAL | Alumbrado |
| 4 | Diego Sosa | `diego@example.org` | AGENTE_MUNICIPAL | Obras Públicas |
| 5 | Fabián Luna | `fabian@example.org` | AGENTE_MUNICIPAL | Higiene Urbana |
| 6 | Gabriela Paz | `gabriela@example.org` | AGENTE_MUNICIPAL | Mantenimiento Vial |
| 7 | Elena Ruiz | `elena@example.org` | ADMINISTRADOR | — |

Los siete entran con la contraseña de desarrollo **`demo1234`**. La base guarda solo su hash BCrypt. Ana y Bruno ya tienen el teléfono verificado, con los números ficticios `+5491100000001` y `+5491100000002`, para poder crear reclamos sin pasar por la verificación. A diferencia del resto de la semilla, las contraseñas se cargan también en una base que ya existía: el script completa las que faltan sin pisar las que se hayan cambiado.

No hay reclamos ni notificaciones precargados. La tabla técnica `inicializaciones` registra el identificador **`datos-dev-v1`** al completar la semilla. El script se invoca en cada arranque de `dev`, pero sus inserciones quedan condicionadas a la ausencia del marcador: la semilla se aplica **una sola vez por base** y las modificaciones posteriores no se restauran en cada reinicio.

En una base existente sin marcador se completan registros y relaciones faltantes sin sobrescribir IDs existentes. Una base nueva vuelve a cargar la semilla. Los ejemplos siguientes presuponen una base recién inicializada, sin cambios manuales en sus IDs o jurisdicciones.

## 10. Probar la API

Abrir otra PowerShell mientras Spring sigue ejecutándose. Los catálogos son públicos:

```powershell
Invoke-RestMethod http://localhost:8080/api/categorias
Invoke-RestMethod http://localhost:8080/api/barrios
Invoke-RestMethod http://localhost:8080/api/areas
```

Todo lo demás exige **iniciar sesión**. El backend guarda la sesión en la cookie `JSESSIONID` y pide, en cada `POST` y `PATCH`, el encabezado `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN` (protección CSRF). PowerShell guarda las cookies en una `WebRequestSession`. Pegar esta función una vez en la terminal:

```powershell
function Entrar($email, $contrasena = "demo1234") {
    $sesion = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    # Cualquier pedido deja la cookie XSRF-TOKEN.
    Invoke-RestMethod http://localhost:8080/api/categorias -WebSession $sesion | Out-Null
    $token = $sesion.Cookies.GetCookies("http://localhost:8080")["XSRF-TOKEN"].Value
    $credenciales = @{ email = $email; contrasena = $contrasena } | ConvertTo-Json
    Invoke-RestMethod http://localhost:8080/api/auth/login -Method Post -WebSession $sesion `
        -Headers @{ "X-XSRF-TOKEN" = $token } -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($credenciales)) | Out-Null
    return @{ Sesion = $sesion; Csrf = @{ "X-XSRF-TOKEN" = $token } }
}
```

Con ella, cada usuario de la semilla (sección 9) queda en una variable:

```powershell
$ana = Entrar "ana@example.org"
Invoke-RestMethod http://localhost:8080/api/auth/yo -WebSession $ana.Sesion
Invoke-RestMethod http://localhost:8080/api/reclamos -WebSession $ana.Sesion
```

`/api/auth/yo` devuelve a Ana Pérez; `/api/reclamos`, sus reclamos (al principio, `[]`). Sin `-WebSession` la respuesta es `401 NO_AUTENTICADO`, y con una contraseña equivocada el login responde `401 CREDENCIALES_INVALIDAS`. La lista completa de usuarios (`/api/usuarios`) solo la ve un administrador.

### Crear y consultar un reclamo

`CrearReclamoRequest` lleva `categoriaId`, `barrioId`, `descripcion` y `direccion`, y opcionalmente `ubicacion` (ver «Probar la ubicación con Georef»). Los IDs deben ser positivos y existir; descripción y dirección deben tener texto, con máximos de 1000 y 200 caracteres respectivamente. El ciudadano es el de la sesión.

```powershell
$body = @{
    categoriaId = 1
    barrioId = 2
    descripcion = "Farol apagado frente a la plaza"
    direccion = "Belgrano 450"
} | ConvertTo-Json

$creado = Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos" `
    -Method Post -WebSession $ana.Sesion -Headers $ana.Csrf `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

$creado | ConvertTo-Json -Depth 10
$numero = $creado.numero

Invoke-RestMethod "http://localhost:8080/api/reclamos/$numero" -WebSession $ana.Sesion | ConvertTo-Json -Depth 10
Invoke-RestMethod "http://localhost:8080/api/reclamos/$numero/notificaciones" -WebSession $ana.Sesion | ConvertTo-Json -Depth 10
```

El POST responde **201 Created**, con `Location: /api/reclamos/{numero}`. Con la semilla y las Strategies predeterminadas, queda `ASIGNADO` a Alumbrado, prioridad `MEDIA`, agente `null`, dos registros de historial y tres notificaciones `INTERNO`: ingreso y asignación para el ciudadano, y el aviso de asignación para la agente de Alumbrado. El número real es generado; reutilizar `$numero`, sin copiar uno ficticio.

Sin `-Headers $ana.Csrf`, el POST responde `403 CSRF_INVALIDO`: es la protección que evita que otro sitio cree reclamos con la sesión del vecino.

Para probar falta de cobertura, usar categoría 1 y barrio 3: queda `INGRESADO`, `area: null`, un historial y una notificación de ingreso; también responde 201. El administrador puede asignarlo manualmente.

### Probar toma, resolución y cierre

Sobre el ejemplo asignado a Alumbrado, la agente Carla Gómez puede tomarlo y resolverlo; Ana puede cerrarlo o reabrirlo cuando está resuelto:

```powershell
$carla = Entrar "carla@example.org"

$bodyEstado = @{ estado = "EN_PROCESO"; observacion = "Cuadrilla en camino" } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos/$numero/estado" `
    -Method Patch -WebSession $carla.Sesion -Headers $carla.Csrf `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($bodyEstado))

$bodyEstado = @{ estado = "RESUELTO"; observacion = "Luminaria reparada" } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos/$numero/estado" `
    -Method Patch -WebSession $carla.Sesion -Headers $carla.Csrf `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($bodyEstado))

$bodyEstado = @{ estado = "CERRADO"; observacion = "Reparacion confirmada" } | ConvertTo-Json
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/reclamos/$numero/estado" `
    -Method Patch -WebSession $ana.Sesion -Headers $ana.Csrf `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($bodyEstado))
```

Cada PATCH exitoso responde 200. El detalle incluye `historial` y `accionesDisponibles` según el actor; el endpoint de notificaciones devuelve todos los avisos del reclamo visible, incluidos los destinados al agente.

### Probar el vencimiento y la auditoría

Una tarea programada revisa cada minuto los reclamos que pasaron su fecha límite sin resolverse. Los marca como vencidos, les sube un nivel de prioridad y genera avisos para el ciudadano y para el área.

Para verlo sin esperar el plazo real, adelantar la fecha límite de un reclamo `ASIGNADO` o `EN_PROCESO`. Abrir la consola de MySQL (pide la contraseña de `.env`):

```powershell
docker compose exec mysql mysql -u reclamos -p reclamos
```

Y ejecutar, reemplazando el número:

```sql
UPDATE reclamos SET fecha_limite = NOW() - INTERVAL 1 DAY WHERE numero = 'REC-XXXXXXXX';
```

Antes de dos minutos, la consulta del reclamo devuelve `"vencido": true` y una prioridad más alta, y el endpoint de notificaciones suma dos avisos. En la interfaz aparece la insignia "Vencido".

Cada operación confirmada deja además una línea de auditoría en el log del backend, con el nombre del evento y el número de reclamo. Se reconocen por el logger `auditoria`:

```text
INFO ... auditoria : ReclamoCreado reclamo=REC-XXXXXXXX fecha=... detalle=...
```

El formato exacto de la línea depende de la configuración de logs; lo que hay que buscar es la palabra `auditoria` seguida del nombre del evento.

### Probar el servicio SOAP de cobertura

En una terminal nueva, desde la raíz del repositorio:

```powershell
cd servicios
.\mvnw.cmd -pl servicio-cobertura spring-boot:run
```

Esperar `Started ServicioCoberturaApplication` y el mensaje `Catálogo de cobertura versión 1 cargado ... 4 áreas`. Abrir `http://localhost:8081/ws/cobertura.wsdl` en el navegador: es el contrato. Para hacer una consulta, usar el ejemplo de PowerShell de [`docs/contratos/README.md`](docs/contratos/README.md#probarlo-a-mano). Bache (2) en Bernal (2) responde Obras Públicas y Mantenimiento Vial; con `categoriaId` 99 responde un SOAP Fault `No existe la categoría 99`.

Para que el backend use el servicio, reiniciarlo con la variable puesta, en la terminal del backend:

```powershell
$env:RECLAMOS_COBERTURA_ORIGEN="soap"
.\mvnw.cmd -pl reclamos-app spring-boot:run
```

Crear un reclamo "Bache" en Bernal: queda `ASIGNADO` y en el log del servicio aparece la línea `ConsultarCobertura municipio=1 barrio=2 categoria=2: 2 áreas`. Después detener el servicio con **Ctrl+C** y crear otro: el alta igual responde 201, el reclamo queda `INGRESADO` con `asignacionPendiente: true` y el detalle muestra que el servicio de cobertura no respondió. El administrador lo asigna a mano.

Para volver a la cobertura local: `Remove-Item Env:RECLAMOS_COBERTURA_ORIGEN` y reiniciar el backend.

### Probar el servicio de prioridad con IA

**Crear la clave de Gemini (una sola vez).** Entrar a `https://aistudio.google.com/apikey` con una cuenta de Google, aceptar los términos y crear una clave. Es gratis y no pide tarjeta. Copiarla en `.env`, en la raíz del repositorio:

```text
GEMINI_API_KEY=la-clave-copiada
```

**Nunca** pegar la clave en el código, en un commit, en un issue ni en el chat del grupo: `.env` ya está en `.gitignore`. Con el nivel gratuito, Google puede usar lo que se le manda para mejorar sus productos, así que solo se prueban reclamos inventados. El servicio nunca recibe nombre, email, DNI, teléfono ni dirección.

En una terminal nueva, desde la raíz:

```powershell
$env:GEMINI_API_KEY="la-clave-copiada"
cd servicios
.\mvnw.cmd -pl servicio-prioridad spring-boot:run
```

Esperar `Started ServicioPrioridadApplication` y la línea `Proveedor de IA: gemini con el modelo ...`. Abrir `http://localhost:8082/swagger-ui.html`, elegir `POST /api/prioridades/evaluar`, **Try it out** y **Execute** con el ejemplo: responde una prioridad, un motivo, `"proveedor": "gemini"` y el modelo. Sin clave responde 503 `PROVEEDOR_NO_DISPONIBLE`. Para probar sin cuenta de Google, `$env:PRIORIDAD_IA_PROVEEDOR="simulado"`: responde con palabras clave y lo aclara en el motivo, porque no es IA.

Para que el backend lo use, reiniciarlo con:

```powershell
$env:RECLAMOS_PRIORIDAD_ESTRATEGIA="ia"
.\mvnw.cmd -pl reclamos-app spring-boot:run
```

Crear un reclamo "Luminaria rota" con la descripción "Poste inclinado con cables expuestos frente a la escuela": la prioridad sale `CRITICA` (o la que sugiera el modelo) con origen `IA` y el motivo que escribió. Después detener el servicio de prioridad con **Ctrl+C** y crear otro: el alta igual responde 201, con la prioridad de la categoría y origen `RESPALDO`.

**Evaluación con los 25 casos.** Con el servicio levantado con la clave, desde la raíz: `java servicios/servicio-prioridad/evaluacion/EvaluarPrioridades.java`. Tarda unos tres minutos y deja el resultado en `docs/evidencias/ia-evaluacion.md`, que se commitea como evidencia.

### Probar la ubicación con Georef

No necesita clave ni ningún servicio extra: el backend consulta Georef, la API oficial de direcciones de Argentina. Hace falta conexión a Internet.

```powershell
$ana = Entrar "ana@example.org"   # la función de la sección 10
Invoke-RestMethod "http://localhost:8080/api/ubicaciones/sugerencias?direccion=Belgrano%20450&barrioId=2" -WebSession $ana.Sesion | ConvertTo-Json -Depth 4
```

Responde `disponible: true` y las sugerencias con coordenadas dentro de Quilmes; para Belgrano 450 hay más de una, porque la calle existe en varias localidades. Con una dirección inventada, la lista vuelve vacía. Sin Internet responde `disponible: false`: no es un error.

En la interfaz, como Ana Pérez: **Crear reclamo**, elegir el barrio, escribir la dirección y tocar **Buscar en el mapa**. Elegir una opción y crear el reclamo: el detalle muestra la dirección normalizada y el enlace **Ver en Google Maps**. Si no se elige ninguna, el reclamo se crea igual y el detalle dice "Sin marcar en el mapa".

### Probar los avisos por correo

Se prueba en el modo demo (sección 8), que levanta RabbitMQ, el servicio de notificaciones y Mailpit. Con el backend levantado a mano la mensajería está apagada: los avisos se guardan como siempre y no sale ningún correo.

1. Abrir Mailpit en `http://localhost:8025`. Ningún correo sale de la máquina: todos quedan ahí.
2. En la interfaz, como Ana Pérez, crear un reclamo. A los pocos segundos llegan a Mailpit los correos "lo recibimos" y, si se asignó, "lo asignamos".
3. Abrir la consola de RabbitMQ en `http://localhost:15672`, con `RABBITMQ_USER` y `RABBITMQ_PASSWORD` de `.env`. En **Exchanges** está `reclamos.eventos`; en **Queues**, `notificaciones.email` y su cola de errores, `notificaciones.email.dlq`.

**Broker caído.** Con `docker compose stop rabbitmq`, crear un reclamo: el alta responde 201 igual. El mensaje queda pendiente en la base:

```powershell
docker compose exec mysql mysql -u reclamos -p reclamos -e "SELECT event_id, tipo_evento, estado, intentos FROM mensajes_salientes ORDER BY id DESC LIMIT 5"
```

Con `docker compose start rabbitmq`, en menos de un minuto el estado pasa a `PUBLICADO` y el correo llega a Mailpit.

**Correo caído.** Con `docker compose stop mailpit`, crear un reclamo: el servicio de notificaciones lo intenta tres veces (`docker compose logs servicio-notificaciones`) y el mensaje termina en `notificaciones.email.dlq`, que se ve en la consola de RabbitMQ. Volver a levantarlo con `docker compose start mailpit`.

Los mensajes no llevan DNI, teléfono ni la descripción del reclamo: solo el número, el estado, el nombre y el correo del destinatario y el texto del aviso. El contrato está en `docs/contratos/mensajes.md`.

### Probar la verificación del teléfono

Un ciudadano necesita el teléfono verificado para crear y reabrir reclamos, y cada número sirve para una sola cuenta. Hay dos modos, que se eligen con `RECLAMOS_TELEGRAM_MODO`:

**Modo simulado (el predeterminado).** No hace falta nada. En la interfaz, **Crear una cuenta**, ir a **Crear reclamo**, tocar **Pedir código de verificación**, escribir un número con el código de país (`+54 9 11 5555-0000`) y **Confirmar teléfono**: aparece el formulario. Con el número de Ana (`+5491100000001`) responde "Ese número ya está verificado en otra cuenta."

**Con el bot real de Telegram.**

1. En Telegram, abrir **@BotFather**, mandar `/newbot` y elegir un nombre y un usuario terminado en `bot` (por ejemplo, `ReclamosQuilmesGrupo10Bot`). BotFather responde con el **token**.
2. En `.env`: `RECLAMOS_TELEGRAM_MODO=telegram`, `TELEGRAM_BOT_TOKEN=` con el token y `TELEGRAM_BOT_USUARIO=` con el usuario, sin `@`. **El token no se pega en ningún otro lado**: quien lo tiene controla el bot. Si se filtra, `/revoke` en BotFather lo invalida.
3. Reiniciar el backend (`docker compose up -d backend`, o volver a cargar `.env` y levantarlo a mano). El log dice `Verificación del teléfono: Telegram[modo=telegram, usuarioBot=<usuario>, token=****]`: el token nunca se muestra.
4. En la interfaz, **Pedir código de verificación**: aparece el código y **abrir en Telegram**. En el celular, el enlace abre el bot; tocar **Iniciar** y después **Compartir mi teléfono**. El bot contesta "Listo" y la pantalla pasa sola al formulario en unos segundos.

El bot consulta Telegram cada 2 segundos (no usa webhook, así que no hace falta una dirección pública). El teléfono nunca se muestra completo: la API lo devuelve como `+54 •••••••0000`.

**Límites.** Hasta 3 códigos por hora y 5 reclamos por día por cuenta (`429 LIMITE_EXCEDIDO`). En el modo demo de Compose el límite diario sube a 20, para poder repetir el recorrido.

**Bloqueo.** Como Elena Ruiz: **Administración** → **Cuentas de ciudadanos** → **Bloquear**. Esa cuenta sigue entrando y viendo sus reclamos, pero no puede crear, reabrir ni verificar el teléfono (`403 CUENTA_BLOQUEADA`). Por API:

```powershell
$elena = Entrar "elena@example.org"
Invoke-RestMethod http://localhost:8080/api/usuarios/2/bloqueo -Method Patch -WebSession $elena.Sesion `
    -Headers $elena.Csrf -ContentType "application/json" -Body '{"bloqueado": true}'
```

**Reaperturas.** Un reclamo resuelto se reabre una sola vez, dentro de los 7 días y con un motivo en la observación. La segunda vez que se resuelve, la interfaz ya no ofrece **Reabrir**; por API, responde `409`.

## 11. Endpoints disponibles

| Método | Ruta | Uso |
| --- | --- | --- |
| POST | `/api/reclamos` | Crear como ciudadano. |
| GET | `/api/reclamos` | Listar según rol; filtro `estado` para todos, `areaId` y `ciudadanoId` solo administrador. |
| GET | `/api/reclamos/{numero}` | Ver detalle, historial y acciones permitidas. |
| PATCH | `/api/reclamos/{numero}/estado` | Cambiar estado; tomar usa `EN_PROCESO`. |
| PATCH | `/api/reclamos/{numero}/asignacion` | Asignar o reasignar como administrador. |
| GET | `/api/reclamos/{numero}/notificaciones` | Consultar avisos del reclamo con los mismos permisos del detalle. |
| GET | `/api/notificaciones` | Bandeja de avisos del usuario de la sesión, del más nuevo al más viejo. |
| GET | `/api/usuarios` | Listar usuarios. Solo administradores. |
| GET | `/api/usuarios/{id}` | Consultar un usuario: el propio, o cualquiera si es administrador. |
| PATCH | `/api/usuarios/{id}/bloqueo` | Bloquear o desbloquear la cuenta de un ciudadano (`{"bloqueado": true}`). Solo administradores. |
| POST | `/api/verificacion-telefono` | Pedir un código para verificar el teléfono. Devuelve el código, el enlace al bot y el vencimiento. |
| POST | `/api/verificacion-telefono/simulada` | Solo en modo simulado: confirmar el código propio con `codigo` y `telefono`. |
| POST | `/api/auth/login` | Iniciar sesión con `email` y `contrasena`. Deja la cookie `JSESSIONID`. |
| GET | `/api/auth/yo` | Usuario de la sesión; `401` sin sesión. |
| POST | `/api/auth/registro` | Crear la cuenta de un ciudadano: `dni`, `nombre`, `apellido`, `email` y `contrasena`. |
| POST | `/api/auth/logout` | Cerrar la sesión. Responde `204`. |
| GET | `/api/categorias` | Listar categorías, SLA y prioridad base. |
| GET | `/api/barrios` | Listar barrios y nombre de municipio. |
| GET | `/api/areas` | Listar áreas, estado activo y cobertura. |
| GET | `/api/ubicaciones/sugerencias` | Buscar una dirección en el mapa (Georef) dentro del municipio del barrio. Parámetros `direccion` y `barrioId`. |
| GET | `/swagger-ui.html` | Swagger UI: documentación de todos los endpoints y un formulario para probarlos. |
| GET | `/v3/api-docs.yaml` | Contrato OpenAPI de la API. |

`CambiarEstadoRequest` admite `estado` obligatorio y `observacion` opcional de hasta 1000 caracteres. `ASIGNADO` debe solicitarse por asignación, nunca por `/estado`. `AsignarReclamoRequest` admite `areaId` obligatorio y positivo, y `observacion` opcional de hasta 1000 caracteres. La asignación manual requiere un área activa y distinta de la actual, y un reclamo `INGRESADO` o `ASIGNADO`; no exige cobertura territorial ni categoría compatibles como la automática.

## 12. Códigos HTTP y errores

| HTTP | Código | Cuándo aparece |
| --- | --- | --- |
| 400 | `DATOS_INVALIDOS` | Falta encabezado/campo, IDs no positivos, JSON o enum inválido, longitud excesiva o intento de `ASIGNADO` por `/estado`. |
| 403 | `ACCESO_DENEGADO` | Rol no habilitado, reclamo de otro ciudadano/área o filtros administrativos sin permiso. |
| 403 | `TELEFONO_NO_VERIFICADO` | Un ciudadano sin teléfono verificado intenta crear o reabrir. |
| 403 | `CUENTA_BLOQUEADA` | Un ciudadano bloqueado intenta crear, reabrir o verificar el teléfono. |
| 429 | `LIMITE_EXCEDIDO` | Más de 5 reclamos en 24 horas o más de 3 códigos de verificación en una hora. |
| 404 | `RECURSO_NO_ENCONTRADO` | Usuario, reclamo, categoría, barrio, área o ruta inexistente. |
| 409 | `REGLA_NEGOCIO` | Transición inexistente, ciudadano inactivo al crear, área inactiva o igual a la actual al asignar, reapertura fuera de límite o número verificado en otra cuenta. |
| 409 | `CONFLICTO_CONCURRENTE` | Otra operación modificó el mismo reclamo al mismo tiempo; volver a cargarlo. |
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

Leer la primera causa real y las líneas `Caused by`, no solo el resumen final de Maven. Distinguir fallo de compilación, test, descarga o arranque/conexión. Los reportes de tests quedan en `target/surefire-reports` dentro de cada módulo. Si al ejecutar `-pl reclamos-app` faltan artefactos `0.1.0-SNAPSHOT`, ejecutar antes `install -DskipTests` desde `backend`. Comprobar también Internet en la primera descarga.

### F. Caracteres como PÃ©rez en PowerShell

Puede ser un problema de decodificación o presentación de la consola, sin implicar datos corruptos. El SQL semilla es UTF-8 y Compose configura MySQL con `utf8mb4`. Revisar el encoding del editor y la terminal; se puede probar PowerShell 7 o comparar la respuesta con otro cliente que decodifique UTF-8. Los ejemplos POST envían bytes UTF-8 explícitos. No modificar datos solo por cómo se ven en una consola.

### G. Docker Desktop no inicia

Comprobar que Docker Desktop esté abierto, use contenedores Linux y que su motor esté listo. Según la instalación de Windows, revisar virtualización habilitada y los requisitos del backend WSL2 de Docker Desktop. No se verificó la configuración de cada PC; este es diagnóstico general. No reinicializar ni eliminar distribuciones o volúmenes para resolverlo sin revisar la causa.

### H. La demo no termina de arrancar o `verificar-demo` marca fallas

`docker compose ps` muestra qué contenedor no llegó a `healthy` y `docker compose logs <servicio>` explica por qué. Lo más común: poca memoria asignada a Docker Desktop (el backend se reinicia o tarda mucho), un puerto ocupado por un backend o frontend que quedó corriendo en la máquina, o la clave de Gemini. La tabla «Si algo sale mal» de `docs/demo.md` tiene cada síntoma con su causa.

### I. 401, 403 CSRF_INVALIDO o "El correo o la contraseña no son correctos"

- **`401 NO_AUTENTICADO`:** falta la sesión. En PowerShell, pasar `-WebSession` con la sesión de `Entrar` (sección 10). En la interfaz, la sesión vence a los 30 minutos sin uso: volver a entrar.
- **`403 CSRF_INVALIDO`:** un `POST` o `PATCH` sin `-Headers $ana.Csrf`, o con el token de otra sesión de PowerShell.
- **`401 CREDENCIALES_INVALIDAS` con `demo1234`:** la base se creó con una versión anterior y el backend no arrancó con el perfil `dev`, que es el que carga las contraseñas. Reiniciar Spring con el perfil `dev` (sección 8).

## 14. Cómo apagar todo

En la terminal del backend y en la del frontend: **Ctrl+C**. En modo demo, **Ctrl+C** en la terminal de `docker compose up` detiene todos los contenedores.

En otra terminal, desde la raíz del repositorio:

```powershell
docker compose stop
```

Para quitar contenedores y red conservando la base:

```powershell
docker compose down
```

`stop` permite volver a iniciar los contenedores; `down` permite recrearlos con `up -d mysql`. Ambos conservan los volúmenes nombrados: la base, la de RabbitMQ y la del servicio de notificaciones. **`down -v` elimina los datos y requiere una decisión explícita de descartarlos.**

## 15. Checklist final

- [ ] La copia está en `main` actualizada.
- [ ] Java 21 activo, también en `.\mvnw.cmd -version`.
- [ ] Docker MySQL aparece `healthy`.
- [ ] Variables cargadas en la terminal que inicia Spring.
- [ ] Tests finalizados con `BUILD SUCCESS`.
- [ ] Artefactos Maven instalados para ejecutar `reclamos-app`.
- [ ] Spring iniciado con perfil `dev` en 8080.
- [ ] `/api/usuarios` responde 200.
- [ ] `Entrar "ana@example.org"` funciona y `/api/reclamos` responde con `-WebSession`.
- [ ] El frontend abre en `http://localhost:5173`, muestra el ingreso y entra con `ana@example.org` y `demo1234`.
- [ ] `servicios`: `.\mvnw.cmd verify` termina con `BUILD SUCCESS`.
- [ ] `.env` tiene `GEMINI_API_KEY` y la clave no aparece en `git status` ni en ningún commit.
- [ ] Modo demo: `docker compose up --build` levanta los ocho contenedores, la interfaz responde, `http://localhost:8081/ws/cobertura.wsdl` muestra el WSDL y `http://localhost:8082/swagger-ui.html` muestra el servicio de IA.
- [ ] Modo demo: al crear un reclamo, el correo llega a Mailpit en `http://localhost:8025`.
- [ ] Modo demo: `docker compose run --rm verificar-demo` termina con `0 fallas`.
