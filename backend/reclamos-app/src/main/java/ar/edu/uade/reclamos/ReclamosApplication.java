package ar.edu.uade.reclamos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada. Está en el paquete raíz para que Spring encuentre sin configuración extra
 * los componentes de todos los módulos (app, persistencia) y las entidades del dominio.
 */
@SpringBootApplication
public class ReclamosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReclamosApplication.class, args);
    }
}
