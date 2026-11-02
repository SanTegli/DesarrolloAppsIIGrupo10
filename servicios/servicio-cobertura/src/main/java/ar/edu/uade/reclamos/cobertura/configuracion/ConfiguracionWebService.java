package ar.edu.uade.reclamos.cobertura.configuracion;

import java.util.List;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Publica el servicio SOAP en {@code /ws}. El WSDL se arma a partir del XSD (contract-first): cada par
 * {@code XxxRequest}/{@code XxxResponse} del esquema es una operación {@code Xxx}.
 *
 * <ul>
 *   <li>{@code /ws/cobertura.wsdl}: el WSDL, con la dirección del servidor que lo pidió.</li>
 *   <li>{@code /ws}: recibe los mensajes SOAP 1.1.</li>
 * </ul>
 *
 * <p>Los pedidos se validan contra el XSD antes de llegar al endpoint: un id que no es un entero positivo
 * se responde con un SOAP Fault de cliente sin consultar el catálogo.
 */
@EnableWs
@Configuration
public class ConfiguracionWebService implements WsConfigurer {

    public static final String NAMESPACE = "http://uade.edu.ar/reclamos/cobertura/v1";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext contexto) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(contexto);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /** El nombre del bean define la URL del WSDL: {@code /ws/cobertura.wsdl}. */
    @Bean(name = "cobertura")
    public DefaultWsdl11Definition cobertura() {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("CoberturaPort");
        wsdl.setServiceName("CoberturaService");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(NAMESPACE);
        wsdl.setSchema(esquemaCobertura());
        return wsdl;
    }

    @Bean
    public XsdSchema esquemaCobertura() {
        return new SimpleXsdSchema(new ClassPathResource("cobertura.xsd"));
    }

    @Bean
    public PayloadValidatingInterceptor validadorDelContrato() {
        PayloadValidatingInterceptor validador = new PayloadValidatingInterceptor();
        validador.setXsdSchema(esquemaCobertura());
        validador.setValidateRequest(true);
        validador.setValidateResponse(true);
        validador.setFaultStringOrReason("El pedido no cumple el contrato cobertura.xsd");
        return validador;
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptores) {
        interceptores.add(validadorDelContrato());
    }
}
