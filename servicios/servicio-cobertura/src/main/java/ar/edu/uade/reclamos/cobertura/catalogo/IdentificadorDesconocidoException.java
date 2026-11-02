package ar.edu.uade.reclamos.cobertura.catalogo;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * La consulta nombra un municipio, barrio o categoría que el catálogo no conoce, o un barrio de otro
 * municipio. Es un error de quien llama: Spring WS la convierte en un SOAP Fault de cliente
 * ({@code SOAP-ENV:Client}) con este mensaje.
 */
@SoapFault(faultCode = FaultCode.CLIENT)
public class IdentificadorDesconocidoException extends RuntimeException {

    public IdentificadorDesconocidoException(String mensaje) {
        super(mensaje);
    }
}
