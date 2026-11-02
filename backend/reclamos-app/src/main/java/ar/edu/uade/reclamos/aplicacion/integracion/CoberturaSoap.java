package ar.edu.uade.reclamos.aplicacion.integracion;

import ar.edu.uade.reclamos.aplicacion.integracion.soap.AreaCubierta;
import ar.edu.uade.reclamos.aplicacion.integracion.soap.ConsultarCoberturaRequest;
import ar.edu.uade.reclamos.aplicacion.integracion.soap.ConsultarCoberturaResponse;
import ar.edu.uade.reclamos.dominio.integracion.CatalogoCobertura;
import ar.edu.uade.reclamos.dominio.integracion.ServicioExternoNoDisponibleException;
import ar.edu.uade.reclamos.dominio.modelo.AreaMunicipal;
import ar.edu.uade.reclamos.dominio.repositorio.AreaMunicipalRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.oxm.XmlMappingException;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.WebServiceException;
import org.springframework.ws.client.core.WebServiceOperations;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.transport.http.ClientHttpRequestMessageSender;

/**
 * Cobertura consultada al servicio SOAP ({@code reclamos.cobertura.origen: soap}).
 *
 * <p>Es un <b>Adapter</b>: implementa la interfaz que el backend ya usaba, {@link CatalogoCobertura}, sobre
 * una interfaz incompatible, la operación {@code ConsultarCobertura} del WSDL. También funciona como
 * <b>proxy remoto</b>: {@code ServicioAsignacion} la llama como a {@link CoberturaLocal} y no sabe que la
 * respuesta viene de otro proceso. Los mensajes XML se quedan en este adaptador (capa anticorrupción): hacia
 * adentro solo salen {@link AreaMunicipal} del dominio.
 *
 * <p>El servicio SOAP decide qué áreas cubren la combinación; el backend conserva el resto de los datos del
 * área. Por eso cada id recibido se carga de la base y se descartan los que no existen, los de áreas
 * inactivas y los de otro municipio.
 *
 * <p>Fallas: si el servicio no responde dentro del tiempo límite, responde algo que no se puede leer o
 * devuelve un SOAP Fault, se lanza {@link ServicioExternoNoDisponibleException} y el reclamo queda con la
 * asignación pendiente. Una lista vacía no es una falla.
 */
public class CoberturaSoap implements CatalogoCobertura {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoberturaSoap.class);

    private final WebServiceOperations soap;
    private final AreaMunicipalRepository areas;

    public CoberturaSoap(WebServiceOperations soap, AreaMunicipalRepository areas) {
        this.soap = Objects.requireNonNull(soap, "soap");
        this.areas = Objects.requireNonNull(areas, "areas");
    }

    /**
     * Cliente SOAP 1.1 para la dirección del servicio, con el mismo tiempo límite para conectar y para leer
     * la respuesta.
     */
    public static WebServiceTemplate crearCliente(String url, Duration tiempoLimite) {
        Jaxb2Marshaller jaxb = new Jaxb2Marshaller();
        jaxb.setClassesToBeBound(ConsultarCoberturaRequest.class, ConsultarCoberturaResponse.class);
        try {
            jaxb.afterPropertiesSet();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo preparar JAXB para el contrato de cobertura", error);
        }

        SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout((int) tiempoLimite.toMillis());
        http.setReadTimeout((int) tiempoLimite.toMillis());

        WebServiceTemplate cliente = new WebServiceTemplate(jaxb);
        cliente.setDefaultUri(url);
        cliente.setMessageSender(new ClientHttpRequestMessageSender(http));
        return cliente;
    }

    @Override
    public List<AreaMunicipal> buscarCandidatas(Long municipioId, Long barrioId, Long categoriaId) {
        ConsultarCoberturaResponse respuesta = consultar(new ConsultarCoberturaRequest(municipioId, barrioId,
                categoriaId));
        List<AreaMunicipal> candidatas = new ArrayList<>();
        respuesta.getAreas().stream().map(AreaCubierta::getId).distinct().sorted().forEach(id -> {
            Optional<AreaMunicipal> area = areas.buscarPorId(id);
            if (area.isEmpty()) {
                LOGGER.warn("El servicio de cobertura devolvió el área {}, que el backend no tiene", id);
            } else if (!Objects.equals(area.get().getMunicipio().getId(), municipioId)) {
                LOGGER.warn("El servicio de cobertura devolvió el área {}, que es de otro municipio", id);
            } else if (area.get().isActiva()) {
                candidatas.add(area.get());
            }
        });
        return candidatas;
    }

    private ConsultarCoberturaResponse consultar(ConsultarCoberturaRequest pedido) {
        Object respuesta;
        try {
            respuesta = soap.marshalSendAndReceive(pedido);
        } catch (SoapFaultClientException fault) {
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de cobertura rechazó la consulta: " + fault.getFaultStringOrReason(), fault);
        } catch (WebServiceException | XmlMappingException error) {
            throw new ServicioExternoNoDisponibleException("El servicio de cobertura no respondió", error);
        }
        if (respuesta instanceof ConsultarCoberturaResponse cobertura) {
            return cobertura;
        }
        throw new ServicioExternoNoDisponibleException("El servicio de cobertura respondió algo inesperado: "
                + (respuesta == null ? "nada" : respuesta.getClass().getSimpleName()));
    }
}
