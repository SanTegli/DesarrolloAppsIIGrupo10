package ar.edu.uade.reclamos.cobertura.endpoint;

import ar.edu.uade.reclamos.cobertura.catalogo.CatalogoCobertura;
import ar.edu.uade.reclamos.cobertura.catalogo.DatosCatalogo;
import ar.edu.uade.reclamos.cobertura.configuracion.ConfiguracionWebService;
import ar.edu.uade.reclamos.cobertura.contrato.Area;
import ar.edu.uade.reclamos.cobertura.contrato.ConsultarCoberturaRequest;
import ar.edu.uade.reclamos.cobertura.contrato.ConsultarCoberturaResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * Operación {@code ConsultarCobertura}. Traduce entre las clases generadas del XSD (paquete
 * {@code contrato}) y el catálogo; no tiene reglas propias.
 */
@Endpoint
public class CoberturaEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoberturaEndpoint.class);

    private final CatalogoCobertura catalogo;

    public CoberturaEndpoint(CatalogoCobertura catalogo) {
        this.catalogo = catalogo;
    }

    @PayloadRoot(namespace = ConfiguracionWebService.NAMESPACE, localPart = "ConsultarCoberturaRequest")
    @ResponsePayload
    public ConsultarCoberturaResponse consultarCobertura(@RequestPayload ConsultarCoberturaRequest pedido) {
        List<DatosCatalogo.Area> areas = catalogo.consultar(pedido.getMunicipioId(), pedido.getBarrioId(),
                pedido.getCategoriaId());
        LOGGER.info("ConsultarCobertura municipio={} barrio={} categoria={}: {} áreas", pedido.getMunicipioId(),
                pedido.getBarrioId(), pedido.getCategoriaId(), areas.size());

        ConsultarCoberturaResponse respuesta = new ConsultarCoberturaResponse();
        for (DatosCatalogo.Area area : areas) {
            Area elemento = new Area();
            elemento.setId(area.id());
            elemento.setNombre(area.nombre());
            respuesta.getArea().add(elemento);
        }
        return respuesta;
    }
}
