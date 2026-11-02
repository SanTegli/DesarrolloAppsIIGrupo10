# Servicios de integración

Aplicaciones de la Segunda Parte que corren separadas del backend. Cada una es un proyecto Spring Boot
con su propio proceso y contenedor; no comparten código con `backend/`: se integran por contrato.

| Servicio | Carpeta | Protocolo | Puerto | PR | Estado |
| --- | --- | --- | --- | --- | --- |
| Cobertura | `servicio-cobertura/` | SOAP 1.1, WSDL en `/ws/cobertura.wsdl` | 8081 | 9 | Hecho |
| Prioridad con IA | `servicio-prioridad/` | REST 2, OpenAPI | 8082 | 10 | Pendiente |
| Notificaciones | `servicio-notificaciones/` | Consume de RabbitMQ, envía SMTP a Mailpit | — | 12 | Pendiente |

Los contratos están en [`docs/contratos/`](../docs/contratos/README.md).

## Cómo compilar y probar

Desde esta carpeta, con Java 21. No hace falta instalar Maven: la carpeta trae su propio wrapper.

```bash
./mvnw verify
```

En Windows, `.\mvnw.cmd verify`. Algunos tests leen archivos de `backend/` y `docs/`, así que se corren
desde el repositorio completo. La integración continua corre el mismo comando en cada pull request.

## Servicio de cobertura (SOAP)

Responde qué áreas municipales atienden una categoría en un barrio. Es el dueño de esa información: la
guarda en `servicio-cobertura/src/main/resources/cobertura.json`, con los mismos ids que la semilla del
backend.

```text
servicio-cobertura/
  src/main/resources/cobertura.xsd     Contrato. Las clases del pedido y la respuesta se generan de acá
  src/main/resources/cobertura.json    Catálogo: municipios, barrios, categorías y qué cubre cada área
  src/main/java/.../configuracion/     Publicación del WSDL, validación contra el XSD y carga del catálogo
  src/main/java/.../endpoint/          CoberturaEndpoint: la operación ConsultarCobertura
  src/main/java/.../catalogo/          CatalogoCobertura: la consulta, sin nada de SOAP
```

Para levantarlo, desde esta carpeta:

```bash
./mvnw -pl servicio-cobertura spring-boot:run
```

Queda escuchando en `http://localhost:8081`. El WSDL está en `http://localhost:8081/ws/cobertura.wsdl` y
[`docs/contratos/README.md`](../docs/contratos/README.md#soap-consultarcobertura) tiene una petición de
ejemplo para PowerShell.

Para que el backend lo use, se arranca el backend con `RECLAMOS_COBERTURA_ORIGEN=soap`. En
`docker compose up --build` ya viene así.

| Propiedad | Valor por defecto | Para qué |
| --- | --- | --- |
| `server.port` | `8081` | Puerto HTTP |
| `cobertura.catalogo` | `classpath:cobertura.json` | Archivo del catálogo; se puede cambiar sin recompilar, por ejemplo `COBERTURA_CATALOGO=file:/datos/cobertura.json` |

Un catálogo inconsistente (un área que cubre un barrio de otro municipio, ids repetidos, una categoría que
no existe) impide arrancar, en lugar de dar respuestas equivocadas después.

| Test | Qué comprueba |
| --- | --- |
| `CatalogoCoberturaTest` | Consultas válidas, lista vacía, ids desconocidos y catálogos inconsistentes |
| `CatalogoSincronizadoTest` | `cobertura.json` y `backend/.../datos-dev.sql` tienen los mismos ids y la misma cobertura |
| `CoberturaEndpointTest` | La operación completa con `MockWebServiceClient`: respuesta, validación contra el XSD y faults |
| `ServicioSoapHttpTest` | El servicio por HTTP real: WSDL publicado, consulta válida y fault con HTTP 500. Regenera `docs/contratos/soap/cobertura.wsdl` |
| `ContratoPublicadoTest` | El XSD del servicio y el de `docs/contratos/soap/` son idénticos |
