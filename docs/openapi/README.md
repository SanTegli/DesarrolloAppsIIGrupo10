# Contratos OpenAPI

| Archivo | API | Cómo se genera |
| --- | --- | --- |
| `reclamos.yaml` | REST 1, la API de reclamos del backend | `OpenApiTest` lo escribe al correr `./mvnw test` en `backend/` |
| `prioridad.yaml` | REST 2, el servicio de prioridad con IA | `OpenApiPrioridadTest` lo escribe al correr `./mvnw verify` en `servicios/` |

Los contratos salen del código con springdoc, así que no se editan a mano: si un cambio en los
controladores o los DTOs cambia el contrato, `./mvnw test` actualiza el archivo y el cambio se commitea
junto con el código. La integración continua también los deja descargables como `openapi-reclamos` y
`openapi-prioridad`.

Con los servicios levantados, los mismos contratos se ven en Swagger UI: `http://localhost:8080/swagger-ui.html`
(REST 1) y `http://localhost:8082/swagger-ui.html` (REST 2).
