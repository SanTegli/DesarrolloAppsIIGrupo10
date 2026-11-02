# Evaluación de la IA de prioridad

**Pendiente de correr con la clave de Gemini.** Este archivo lo reemplaza `EvaluarPrioridades.java` con
los resultados reales: aciertos sobre los 25 casos de
[`casos.tsv`](../../servicios/servicio-prioridad/evaluacion/casos.tsv), casos en que la IA puso menos
prioridad de la esperada y tiempos de respuesta.

Cómo generarlo, desde la raíz del repositorio y con el servicio de prioridad levantado con
`GEMINI_API_KEY` (ver [servicios/README.md](../../servicios/README.md#evaluación-con-los-25-casos)):

```powershell
java servicios/servicio-prioridad/evaluacion/EvaluarPrioridades.java
```
