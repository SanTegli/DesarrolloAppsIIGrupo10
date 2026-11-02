/**
 * Mensajes del contrato SOAP de cobertura ({@code docs/contratos/soap/cobertura.xsd}) vistos desde el
 * backend. Son la capa anticorrupción de {@code CoberturaSoap}: solo esa clase los usa, y lo que sale de
 * acá hacia el resto del backend son {@code AreaMunicipal} del dominio.
 *
 * <p>Están escritos a mano, no generados: son tres clases y el backend no publica el servicio.
 * {@code MensajesCoberturaSoapTest} comprueba que lo que producen y aceptan cumple el XSD.
 */
@XmlSchema(namespace = "http://uade.edu.ar/reclamos/cobertura/v1", elementFormDefault = XmlNsForm.QUALIFIED)
package ar.edu.uade.reclamos.aplicacion.integracion.soap;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
