# Evidencias de ejecución

Cómo demostrar cada caso de la Primera Parte: qué test automático lo cubre y cómo reproducirlo a
mano desde la interfaz.

## Tests automáticos

```bash
cd backend
./mvnw test          # dominio, persistencia con H2, servicios y REST

cd ../frontend
npm install
npm test             # 28 tests: capa de API y componentes
```

Guardar en esta carpeta la salida de los dos comandos (`BUILD SUCCESS` y el resumen de Vitest).

## Casos a demostrar

Los usuarios, barrios, categorías y áreas son los de la semilla (ver
[contrato REST](../api-contract.md#datos-semilla-acordados)).

| Caso | Qué demuestra | Test que lo cubre | Cómo reproducirlo en la interfaz |
| --- | --- | --- | --- |
| CP01 | Crear reclamo válido: `201` y `ASIGNADO` | `RestIntegrationTest.postValidoDevuelve201LocationDtoCompletoYNotificaciones` | Como Ana Pérez, crear "Luminaria rota" en Bernal. Queda asignado a Alumbrado |
| CP02 | Usuario, barrio o categoría inexistente: `404` | `RestIntegrationTest.postUsuarioInexistenteEs404` | Solo por API: `POST /api/reclamos` con `X-Usuario-Id: 999` |
| CP03 | Datos inválidos: `400` | `RestIntegrationTest.postConBodyInvalidoEs400`, `beanValidationDevuelveUnDetallePorCampoInvalido` | Solo por API: enviar `descripcion` vacía. El formulario no deja enviar campos vacíos |
| CP04 | Cambiar la estrategia de prioridad sin tocar `ReclamoService` | `ConfiguracionAplicacionTest.seleccionaLasEstrategiasAlternativasPorPropiedades`, `PrioridadTest` | Poner `reclamos.prioridad.estrategia: palabras-clave`, reiniciar y crear un reclamo con la palabra "peligro". Sale con prioridad Alta |
| CP05 | Cambiar la estrategia de asignación por configuración | `ConfiguracionAplicacionTest`, `AsignacionTest` | Poner `reclamos.asignacion.estrategia: carga-trabajo`, reiniciar y crear dos reclamos "Bache" en Bernal. Se reparten entre Obras Públicas y Mantenimiento Vial |
| CP06 | Sin área compatible: `201` e `INGRESADO`; después asigna el administrador | `RestIntegrationTest.postSinAreaCompatibleDevuelveIngresadoYAreaNula`, `patchAsignacionValidaEs200` | Como Ana Pérez, crear "Residuos" en Ezpeleta. Como Elena Ruiz, ir a Administración, abrirlo y asignarle un área |
| CP07 | Crear y asignar generan eventos | `FlujoDeAvisosTest`, `ObserverIntegrationTest` | Abrir cualquier reclamo: "Avisos enviados" lista los avisos al ciudadano y a los agentes. En el log del backend, las líneas del logger `auditoria` muestran cada evento |
| CP08 | Flujo `ASIGNADO`, `EN_PROCESO`, `RESUELTO`, `CERRADO` | `RestIntegrationTest.patchEstadoValidoPersisteYMapeaAgenteHistorialYAcciones`, `ReclamoTest.flujoPrincipal` | Como Carla Gómez, tomar y resolver el reclamo de CP01. Como Ana Pérez, confirmar la solución |
| CP09 | Rol incorrecto: `403` | `RestIntegrationTest.patchEstadoSinPermisoEs403`, `postAgenteNoPuedeCrearEs403` | Solo por API. La interfaz no muestra las acciones que el usuario no puede hacer |
| CP10 | Transición inexistente: `409` | `RestIntegrationTest.patchTransicionInvalidaPrevaleceSobrePermisosYEs409`, `PoliticaTransicionesTest` | Solo por API: `PATCH .../estado` con `RESUELTO` sobre un reclamo `INGRESADO` |
| CP11 | Persistencia de reclamo, barrio, área, categoría e historial | `ReclamoRepositoryJpaTest.guardaYRecuperaPorNumeroConSusRelaciones` | Después de CP08, consultar las tablas `reclamos` e `historial_estados` en MySQL |
| CP12 | Consultas por número, ciudadano y área | `ReclamoRepositoryJpaTest.filtraPorCiudadanoYAreaYOrdenaDelMasNuevoAlMasViejo`, `RestIntegrationTest.listadosPorRolConservanOrdenYSoloIncluyenCamposDelResumen` | El listado de Ana muestra solo sus reclamos; el de Carla, los de Alumbrado; el de Elena, todos, con filtro por área |

| CP13 | Vencimiento: se marca, sube la prioridad y publica `ReclamoVencido` | `ServicioVencimientosTest`, `FlujoDeAvisosTest.elVencimientoPublicaReclamoVencidoYAvisaAlCiudadanoYAlArea` | Con un reclamo abierto, adelantar su `fecha_limite` en MySQL y esperar un minuto. Aparece la insignia "Vencido" y un aviso nuevo |

Para CP13, con el número del reclamo:

```sql
UPDATE reclamos SET fecha_limite = NOW() - INTERVAL 1 DAY WHERE numero = 'REC-XXXXXXXX';
```

Los casos "solo por API" se prueban con los comandos de PowerShell de
[README_INSTRUCTIVO.md](../../README_INSTRUCTIVO.md).

## Capturas de la interfaz

Las capturas de `interfaz/` muestran el recorrido completo de los tres roles. **Se tomaron con la
API simulada** (`npm run mock`), no con el backend real: sirven para ver la interfaz, no como
evidencia de la integración.

| Captura | Qué muestra |
| --- | --- |
| [02-formulario-crear](interfaz/02-formulario-crear.png) | Ciudadano: alta de un reclamo |
| [03-detalle-asignado-ciudadano](interfaz/03-detalle-asignado-ciudadano.png) | Reclamo asignado automáticamente, con historial y avisos |
| [04-detalle-ingresado-sin-area](interfaz/04-detalle-ingresado-sin-area.png) | Reclamo sin área compatible |
| [06-agente-listado](interfaz/06-agente-listado.png) | Agente: reclamos de su área |
| [07-agente-en-proceso](interfaz/07-agente-en-proceso.png) | Agente: reclamo tomado, listo para resolver |
| [08-admin-listado](interfaz/08-admin-listado.png) | Administrador: todos los reclamos, con filtros |
| [09-admin-administracion](interfaz/09-admin-administracion.png) | Administrador: reclamos sin asignar y cobertura de las áreas |
| [10-admin-asignacion-manual](interfaz/10-admin-asignacion-manual.png) | Administrador: asignación manual y rechazo |
| [13-ciudadano-cerrado](interfaz/13-ciudadano-cerrado.png) | Flujo completo: historial de cinco pasos y avisos |

## Pendiente antes de entregar

Evidencias que hay que capturar con el sistema real levantado (MySQL, backend y frontend):

- [ ] Salida de `./mvnw test` con `BUILD SUCCESS`.
- [ ] Salida de `npm test`.
- [ ] Repetir el recorrido de las capturas contra el backend real y reemplazarlas.
- [ ] Consulta SQL de `reclamos`, `historial_estados` y `notificaciones` después del flujo de CP08.
- [ ] Log del backend al arrancar con `palabras-clave` y con `carga-trabajo` (CP04 y CP05).
- [ ] Líneas del logger `auditoria` y de `TareaVencimientos` en el log del backend (CP07 y CP13).
- [ ] Respuestas `400`, `403`, `404` y `409` de los casos "solo por API".
