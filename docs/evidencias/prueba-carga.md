# Prueba de carga básica

Corrida el 17/11/2026, 12:47:08 contra `http://localhost:8080`: 20 usuarios simultáneos durante 30 s, solo lecturas, con la sesión de ana@example.org.

| Endpoint | Pedidos | Errores | p50 (ms) | p95 (ms) | Máximo (ms) |
| --- | --- | --- | --- | --- | --- |
| `GET /api/categorias` | 4594 | 0 | 13 | 30 | 198 |
| `GET /api/dashboard/publico` | 4595 | 0 | 13 | 32 | 280 |
| `GET /api/reclamos` | 4599 | 0 | 20 | 56 | 227 |
| `GET /api/notificaciones` | 4597 | 0 | 26 | 72 | 282 |
| `GET /api/reclamos/{numero}` | 4593 | 0 | 34 | 104 | 316 |
| **Total** | 22978 | 0 | 19 | 66 | 316 |

Rendimiento: 765.7 pedidos por segundo.

Meta (RNF-3): ningún error y percentil 95 ≤ 1000 ms. Resultado: **cumple**.
