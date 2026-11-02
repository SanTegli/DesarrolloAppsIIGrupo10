package ar.edu.uade.reclamos.cobertura;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio SOAP de cobertura. Es un componente aparte del backend, con su proceso y su contenedor: solo
 * comparte con él el contrato {@code cobertura.xsd}. WSDL en {@code http://localhost:8081/ws/cobertura.wsdl}.
 */
@SpringBootApplication
public class ServicioCoberturaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioCoberturaApplication.class, args);
    }
}
