package ar.edu.uade.reclamos.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Consumidor de los avisos: lee la cola {@code notificaciones.email} de RabbitMQ y manda cada aviso por correo.
 * Es un componente aparte del backend, con su proceso, su contenedor y su propia base: solo comparte con él el
 * contrato de mensajes ({@code docs/contratos/mensajes.md}). No publica ningún puerto HTTP.
 */
@SpringBootApplication
public class ServicioNotificacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioNotificacionesApplication.class, args);
    }
}
