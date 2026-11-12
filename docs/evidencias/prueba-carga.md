# Prueba de carga básica

Corrida el 11/11/2026, 10:28:55 contra `http://localhost:8080`: 20 usuarios simultáneos durante 30 s, solo lecturas, con la sesión de ana@example.org.

| Endpoint | Pedidos | Errores | p50 (ms) | p95 (ms) | Máximo (ms) |
| --- | --- | --- | --- | --- | --- |
| `GET /api/categorias` | 4357 | 0 | 15 | 30 | 149 |
| `GET /api/dashboard/publico` | 4360 | 0 | 16 | 32 | 149 |
| `GET /api/reclamos` | 4356 | 0 | 23 | 50 | 171 |
| `GET /api/notificaciones` | 4358 | 0 | 25 | 58 | 177 |
| `GET /api/reclamos/{numero}` | 4357 | 0 | 40 | 99 | 243 |
| **Total** | 21788 | 0 | 22 | 62 | 243 |

Rendimiento: 726 pedidos por segundo.

Meta (RNF-3): ningún error y percentil 95 ≤ 1000 ms. Resultado: **cumple**.
