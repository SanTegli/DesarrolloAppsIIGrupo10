# Despliegue

El sistema se despliega en ocho contenedores. El navegador solo habla con el contenedor del
frontend, que sirve la interfaz y reenvía las llamadas a `/api` al backend. El backend consulta la
cobertura al servicio SOAP y la prioridad al servicio de IA por la red interna de Compose. Salen a
Internet el servicio de IA, a la API de Gemini, y el backend, a Georef para ubicar las direcciones.

```mermaid
flowchart LR
  subgraph PC["PC del usuario"]
    NAV["Navegador"]
  end
  subgraph DOCKER["Host con Docker Compose"]
    subgraph CF["Contenedor reclamos-frontend"]
      NGINX["nginx<br/>archivos de React + proxy de /api"]
    end
    subgraph CB["Contenedor reclamos-backend"]
      APP["reclamos-app.jar<br/>Spring Boot sobre Java 21"]
    end
    subgraph CM["Contenedor reclamos-mysql"]
      DB[("MySQL 8.4<br/>base reclamos")]
    end
    subgraph CS["Contenedor servicio-cobertura"]
      SOAP["servicio-cobertura.jar<br/>SOAP, catálogo en memoria"]
    end
    subgraph CP["Contenedor servicio-prioridad"]
      IA["servicio-prioridad.jar<br/>REST 2, sin base de datos"]
    end
    subgraph CR["Contenedor reclamos-rabbitmq"]
      MQ[("RabbitMQ 4.1<br/>reclamos.eventos")]
    end
    subgraph CN["Contenedor servicio-notificaciones"]
      NOT["servicio-notificaciones.jar<br/>consumidor, H2 propia"]
    end
    subgraph CMP["Contenedor reclamos-mailpit"]
      MP["Mailpit<br/>SMTP y buzón web"]
    end
    VOL[["Volumen reclamos-mysql-data"]]
  end
  GEM["API de Gemini<br/>generativelanguage.googleapis.com"]
  GEO["Georef<br/>apis.datos.gob.ar"]

  NAV -->|"HTTP 5173"| NGINX
  NGINX -->|"HTTP 8080, /api"| APP
  APP -->|"JDBC 3306"| DB
  APP -->|"SOAP sobre HTTP 8081, /ws"| SOAP
  APP -->|"REST 8082, /api/prioridades"| IA
  IA -->|"HTTPS, GEMINI_API_KEY"| GEM
  APP -->|"HTTPS, sin clave"| GEO
  APP -->|"AMQP 5672"| MQ
  MQ -->|"AMQP 5672"| NOT
  NOT -->|"SMTP 1025"| MP
  NAV -.->|"HTTP 8025, buzón"| MP
  DB --- VOL
```

| Contenedor | Imagen | Puerto publicado | Qué contiene |
| --- | --- | --- | --- |
| `reclamos-frontend` | `nginx:1.27-alpine`, construida desde `frontend/Dockerfile` | 5173 | Interfaz React compilada y proxy de `/api` |
| `reclamos-backend` | `eclipse-temurin:21-jre`, construida desde `backend/Dockerfile` | 8080 | `reclamos-app.jar`, que incluye los cuatro módulos Maven |
| `reclamos-mysql` | `mysql:8.4` | 3306 | Base de datos, con los datos en un volumen |
| `servicio-cobertura` | `eclipse-temurin:21-jre`, construida desde `servicios/servicio-cobertura/Dockerfile` | 8081 | Servicio SOAP de cobertura; el WSDL queda en `http://localhost:8081/ws/cobertura.wsdl` |
| `servicio-prioridad` | `eclipse-temurin:21-jre`, construida desde `servicios/servicio-prioridad/Dockerfile` | 8082 | Servicio de prioridad con IA (REST 2); Swagger UI en `http://localhost:8082/swagger-ui.html` |
| `reclamos-rabbitmq` | `rabbitmq:4.1-management` | 5672 y 15672 | Broker de mensajes, con sus datos en un volumen; consola web en `http://localhost:15672` |
| `servicio-notificaciones` | `eclipse-temurin:21-jre`, construida desde `servicios/servicio-notificaciones/Dockerfile` | Ninguno | Consumidor de la cola `notificaciones.email`; guarda los `eventId` procesados en el volumen `notificaciones-datos` |
| `reclamos-mailpit` | `axllent/mailpit` | 1025 y 8025 | SMTP de prueba; los correos se ven en `http://localhost:8025` |

Los puertos 8081 y 8082 se publican para poder abrir el WSDL y Swagger y probar los servicios desde la
máquina; el backend no los necesita, porque llega por la red de Compose con el nombre de cada servicio.

## Configuración externalizada

| Variable | Dónde se usa | Para qué |
| --- | --- | --- |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DB_PORT` | `.env`, leído por Compose | Credenciales y puerto de MySQL |
| `DB_URL` | Contenedor del backend | URL de conexión; apunta al servicio `mysql` de la red de Compose |
| `reclamos.prioridad.estrategia`, `reclamos.asignacion.estrategia` | `application.yml` | Estrategias activas |
| `reclamos.vencimientos.habilitado`, `reclamos.vencimientos.intervalo-ms` | `application.yml` | Control de vencimientos |
| `reclamos.cobertura.origen` | `application.yml`; `RECLAMOS_COBERTURA_ORIGEN` en Compose | De dónde salen las áreas candidatas: `local` (la base) o `soap`. En Compose es `soap` |
| `reclamos.cobertura.url` | `application.yml`; `RECLAMOS_COBERTURA_URL` en Compose | Dirección del servicio SOAP: `http://localhost:8081/ws` en desarrollo, `http://servicio-cobertura:8081/ws` entre contenedores |
| `reclamos.cobertura.tiempo-limite` | `application.yml` | Cuánto se espera al servicio SOAP antes de dejar la asignación pendiente; 2 segundos |
| `cobertura.catalogo` | `application.yml` de `servicio-cobertura` | Archivo del catálogo de cobertura |
| `reclamos.prioridad.estrategia` en `ia` | `RECLAMOS_PRIORIDAD_ESTRATEGIA` en Compose | La prioridad la sugiere el servicio de IA, con respaldo por categoría |
| `reclamos.prioridad.ia.*` | `application.yml`; `RECLAMOS_PRIORIDAD_IA_URL` en Compose | Dirección de REST 2, tiempos (1 s para conectar, 6 s para responder) y circuit breaker |
| `GEMINI_API_KEY` | `.env`, leído por Compose; contenedor `servicio-prioridad` | Clave de Gemini. Sin clave el servicio responde 503 y el backend usa el respaldo. **Nunca se commitea** |
| `PRIORIDAD_IA_PROVEEDOR` | `.env`; contenedor `servicio-prioridad` | `gemini` (por defecto), `openai` o `simulado` |
| `OPENAI_API_KEY` | `.env`; contenedor `servicio-prioridad` | Solo si se usa `openai` |
| `reclamos.geocodificacion.*` | `application.yml` | Georef: URL, tiempos (1 s y 3 s), cantidad de sugerencias, caché de 10 minutos y recuadro de cada municipio. No usa clave |
| `reclamos.mensajeria.habilitada` | `application.yml` (`false`); `RECLAMOS_MENSAJERIA_HABILITADA` en Compose (`true`) | Outbox y publicación en RabbitMQ. Apagada, los avisos quedan solo internos |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `.env`, leído por Compose; backend y `servicio-notificaciones` | Usuario de RabbitMQ. El usuario `guest` solo entra desde el mismo contenedor, por eso Compose crea este |
| `RABBITMQ_HOST`, `SMTP_HOST`, `SMTP_PORT` | Contenedores del backend y del consumidor | `rabbitmq` y `mailpit` entre contenedores; `localhost` en desarrollo |
| `springdoc.*` | `application.yml` | Swagger UI en `/swagger-ui.html`, publicado por el puerto 8080 del backend |
| `VITE_BACKEND_URL` | `frontend/.env`, solo en desarrollo | Adónde reenvía Vite las llamadas a `/api` |

## Dos formas de ejecutar

| Modo | MySQL | Backend | Frontend | Comando |
| --- | --- | --- | --- | --- |
| Desarrollo | Contenedor | En la máquina, con Maven | En la máquina, con Vite | `docker compose up -d mysql` y después backend y frontend a mano |
| Demo | Contenedor | Contenedor | Contenedor | `docker compose up --build` |

En desarrollo el backend usa la cobertura local; para probar el SOAP se levanta `servicio-cobertura` con
`./mvnw -pl servicio-cobertura spring-boot:run` desde `servicios/` (o `docker compose up -d servicio-cobertura`)
y se arranca el backend con `RECLAMOS_COBERTURA_ORIGEN=soap`. Lo mismo con la IA: se levanta
`servicio-prioridad` y se arranca el backend con `RECLAMOS_PRIORIDAD_ESTRATEGIA=ia`.

En desarrollo el proxy de `/api` lo hace Vite; en la demo lo hace nginx. Para el navegador es lo
mismo: siempre llama a `/api` en el origen de la interfaz.
