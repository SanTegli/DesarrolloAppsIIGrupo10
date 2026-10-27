# Servicios de integración

Aplicaciones de la Segunda Parte que corren separadas del backend. Cada una es un proyecto Spring Boot
con su propio proceso y contenedor; no comparten código con `backend/`: se integran por contrato.

| Servicio | Carpeta | Protocolo | Puerto | PR |
| --- | --- | --- | --- | --- |
| Cobertura | `servicio-cobertura/` | SOAP, WSDL en `/ws/cobertura.wsdl` | 8081 | 9 |
| Prioridad con IA | `servicio-prioridad/` | REST 2, OpenAPI | 8082 | 10 |
| Notificaciones | `servicio-notificaciones/` | Consume de RabbitMQ, envía SMTP a Mailpit | — | 12 |

Los contratos de partida están en [`docs/contratos/`](../docs/contratos/README.md).

## Cómo compilar y probar

Desde esta carpeta, con Maven 3.9 o posterior instalado:

```bash
mvn -B verify
```

Cada PR que agrega un servicio lo suma a `<modules>` en `pom.xml`. La integración continua corre este
comando en cada pull request.
