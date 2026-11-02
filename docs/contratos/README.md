# Contratos de integración

Contratos de partida de la Segunda Parte. Los cuatro PRs de integración arrancan de acá; si alguien
necesita cambiar un contrato, lo avisa al grupo antes, igual que con [el contrato REST](../api-contract.md).

| Integración | Contrato | Lo implementa |
| --- | --- | --- |
| SOAP de cobertura | [`soap/cobertura.xsd`](soap/cobertura.xsd) y [`soap/cobertura.wsdl`](soap/cobertura.wsdl) | Hecho en el PR 9: `servicios/servicio-cobertura` y `CoberturaSoap` en el backend |
| REST 2 de prioridad con IA | [`rest2-prioridad.yaml`](rest2-prioridad.yaml) y, generado del código, [`../openapi/prioridad.yaml`](../openapi/README.md) | Hecho en el PR 10: `servicios/servicio-prioridad` y `PrioridadPorIA` en el backend |
| Mensajes por RabbitMQ | [`mensajes.md`](mensajes.md) | PR 12, backend y `servicios/servicio-notificaciones` |
| REST 1 (API de reclamos) | [`../openapi/reclamos.yaml`](../openapi/README.md), generado del código | Backend |

## SOAP: `ConsultarCobertura`

El servicio corre en el puerto 8081. El WSDL se genera del XSD (contract-first) y se publica en
`http://localhost:8081/ws/cobertura.wsdl`; los mensajes van por `POST http://localhost:8081/ws`, SOAP 1.1.

| Elemento del WSDL | Valor |
| --- | --- |
| Namespace | `http://uade.edu.ar/reclamos/cobertura/v1` |
| `portType` | `CoberturaPort`, una operación: `ConsultarCobertura` |
| `service` | `CoberturaService` |
| Mensajes | `ConsultarCoberturaRequest` y `ConsultarCoberturaResponse` |

El `/v1` del namespace es la versión del contrato. Un cambio incompatible publica `/v2` al lado, en lugar
de modificar este. `cobertura.xsd` en esta carpeta es copia exacta del que usa el servicio
(`ContratoPublicadoTest` lo controla), y `cobertura.wsdl` lo regenera `ServicioSoapHttpTest` en cada
corrida de tests: si cambió, se commitea junto con el código.

### Petición y respuesta

`ConsultarCobertura` para Bache (categoría 2) en Bernal (barrio 2) de Quilmes (municipio 1):

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:cob="http://uade.edu.ar/reclamos/cobertura/v1">
  <soapenv:Body>
    <cob:ConsultarCoberturaRequest>
      <cob:municipioId>1</cob:municipioId>
      <cob:barrioId>2</cob:barrioId>
      <cob:categoriaId>2</cob:categoriaId>
    </cob:ConsultarCoberturaRequest>
  </soapenv:Body>
</soapenv:Envelope>
```

```xml
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Body>
    <ns2:ConsultarCoberturaResponse xmlns:ns2="http://uade.edu.ar/reclamos/cobertura/v1">
      <ns2:area><ns2:id>2</ns2:id><ns2:nombre>Obras Públicas</ns2:nombre></ns2:area>
      <ns2:area><ns2:id>4</ns2:id><ns2:nombre>Mantenimiento Vial</ns2:nombre></ns2:area>
    </ns2:ConsultarCoberturaResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

El prefijo de los elementos (`cob:`, `ns2:`) lo elige cada lado; lo que cuenta es el namespace.

### Respuestas posibles

| Caso | Respuesta | Qué hace el backend |
| --- | --- | --- |
| Hay áreas que cubren la combinación | HTTP 200 con un `area` por cada una, ordenadas por id | Carga cada área de su base, descarta las inactivas y la estrategia elige |
| Ninguna área la cubre | HTTP 200 sin elementos `area`. No es un error | El reclamo queda `INGRESADO`, como con la cobertura local |
| Id inexistente o barrio de otro municipio | HTTP 500 con SOAP Fault `SOAP-ENV:Client` y el motivo, por ejemplo `No existe el barrio 99` | `INGRESADO` con `asignacionPendiente: true` |
| Id que no cumple el XSD (cero, negativo, texto) o falta un campo | SOAP Fault `SOAP-ENV:Client`: `El pedido no cumple el contrato cobertura.xsd`. No llega al catálogo | Igual que el anterior |
| El servicio no contesta en `reclamos.cobertura.tiempo-limite` (2 segundos) | — | `INGRESADO` con `asignacionPendiente: true` |

### Probarlo a mano

Con el servicio levantado ([cómo](../../servicios/README.md)), en PowerShell:

```powershell
$sobre = @'
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cob="http://uade.edu.ar/reclamos/cobertura/v1">
  <soapenv:Body><cob:ConsultarCoberturaRequest>
    <cob:municipioId>1</cob:municipioId><cob:barrioId>2</cob:barrioId><cob:categoriaId>2</cob:categoriaId>
  </cob:ConsultarCoberturaRequest></soapenv:Body>
</soapenv:Envelope>
'@
Invoke-WebRequest http://localhost:8081/ws -Method Post -ContentType "text/xml; charset=utf-8" -Body $sobre |
  Select-Object -ExpandProperty Content
```

También se puede importar `http://localhost:8081/ws/cobertura.wsdl` en SoapUI o en Postman, que arman la
petición a partir del WSDL.

### Quién es dueño de qué

El servicio SOAP es el dueño de la cobertura: qué área atiende qué categoría en qué barrio. La guarda en
[`cobertura.json`](../../servicios/servicio-cobertura/src/main/resources/cobertura.json), con los mismos ids
que la semilla del backend; `CatalogoSincronizadoTest` falla si alguien cambia uno solo de los dos. El
backend conserva los datos de cada área (nombre, email, si está activa) y su propia tabla de cobertura, que
sigue usando con `reclamos.cobertura.origen: local`.

## REST 2: `POST /api/prioridades/evaluar`

El servicio de prioridad corre en el puerto 8082, con Swagger UI en `http://localhost:8082/swagger-ui.html`.
Recibe solo la categoría y la descripción del reclamo: nunca el nombre, el email, el DNI, el teléfono, la
dirección ni la foto del vecino.

```json
{ "categoria": "Luminaria rota", "descripcion": "Poste inclinado con cables expuestos frente a la escuela" }
```

```json
{
  "prioridad": "CRITICA",
  "motivo": "Hay cables expuestos frente a una escuela: riesgo eléctrico inmediato.",
  "proveedor": "gemini",
  "modelo": "gemini-3.5-flash-lite"
}
```

| HTTP | Código | Cuándo | Qué hace el backend |
| --- | --- | --- | --- |
| 200 | — | El modelo sugirió una prioridad válida | La usa si no es menor que la de la categoría: origen `IA` |
| 400 | `DATOS_INVALIDOS` | Falta la categoría o la descripción, o son demasiado largas | Respaldo por categoría |
| 502 | `RESPUESTA_INVALIDA` | El modelo respondió algo que no es una de las cuatro prioridades con un motivo | Respaldo por categoría |
| 503 | `PROVEEDOR_NO_DISPONIBLE` | Falta la clave, el proveedor la rechazó, se agotó la cuota o el proveedor falló | Respaldo por categoría |
| 504 | `TIEMPO_AGOTADO` | El modelo no contestó en 5 segundos | Respaldo por categoría |

En todos los casos de error el reclamo se crea igual, con origen `RESPALDO`. El cuerpo de error es el
mismo que el de REST 1.

### Probarlo a mano

Con el servicio levantado ([cómo](../../servicios/README.md#servicio-de-prioridad-con-ia-rest-2)), en
PowerShell:

```powershell
Invoke-RestMethod http://localhost:8082/api/prioridades/evaluar -Method Post -ContentType "application/json; charset=utf-8" `
  -Body '{"categoria": "Luminaria rota", "descripcion": "Poste inclinado con cables expuestos frente a la escuela"}'
```

O desde Swagger UI: `POST /api/prioridades/evaluar`, **Try it out** y **Execute**.
