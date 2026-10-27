# Contratos de integración

Contratos de partida de la Segunda Parte. Los cuatro PRs de integración arrancan de acá; si alguien
necesita cambiar un contrato, lo avisa al grupo antes, igual que con [el contrato REST](../api-contract.md).

| Integración | Contrato | Lo implementa |
| --- | --- | --- |
| SOAP de cobertura | [`soap/cobertura.xsd`](soap/cobertura.xsd) | PR 9, `servicios/servicio-cobertura` |
| REST 2 de prioridad con IA | [`rest2-prioridad.yaml`](rest2-prioridad.yaml) (OpenAPI 3.0) | PR 10, `servicios/servicio-prioridad` |
| Mensajes por RabbitMQ | [`mensajes.md`](mensajes.md) | PR 12, backend y `servicios/servicio-notificaciones` |
| REST 1 (API de reclamos) | [`../openapi/reclamos.yaml`](../openapi/README.md), generado del código | Backend |

## SOAP: ejemplo de petición y respuesta

`ConsultarCobertura` para Bache (categoría 2) en Bernal (barrio 2) de Quilmes (municipio 1):

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:cob="http://uade.edu.ar/reclamos/cobertura">
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
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:cob="http://uade.edu.ar/reclamos/cobertura">
  <soapenv:Body>
    <cob:ConsultarCoberturaResponse>
      <cob:area><cob:id>2</cob:id><cob:nombre>Obras Públicas</cob:nombre></cob:area>
      <cob:area><cob:id>4</cob:id><cob:nombre>Mantenimiento Vial</cob:nombre></cob:area>
    </cob:ConsultarCoberturaResponse>
  </soapenv:Body>
</soapenv:Envelope>
```

Un id que no existe devuelve un SOAP Fault de cliente (`soapenv:Client`) con el motivo. Una combinación sin
áreas devuelve la respuesta sin elementos `area`: no es un error.

En el backend, `CoberturaSoap` implementa `CatalogoCobertura`. Si el servicio no responde en 2 segundos, el
reclamo queda `INGRESADO` con `asignacionPendiente: true`.
