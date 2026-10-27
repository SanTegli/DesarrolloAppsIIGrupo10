# Contratos OpenAPI

| Archivo | API | Cómo se genera |
| --- | --- | --- |
| `reclamos.yaml` | REST 1, la API de reclamos del backend | `OpenApiTest` lo escribe al correr `./mvnw test` en `backend/` |
| `prioridad.yaml` | REST 2, el servicio de prioridad con IA | Lo agrega el PR 10, generado del servicio |

Los contratos salen del código con springdoc, así que no se editan a mano: si un cambio en los
controladores o los DTOs cambia el contrato, `./mvnw test` actualiza el archivo y el cambio se commitea
junto con el código. La integración continua también lo deja descargable como `openapi-reclamos`.

Con el backend levantado, el mismo contrato se ve en Swagger UI: `http://localhost:8080/swagger-ui.html`.
