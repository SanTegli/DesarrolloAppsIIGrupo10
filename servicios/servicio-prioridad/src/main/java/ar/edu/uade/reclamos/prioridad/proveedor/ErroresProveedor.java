package ar.edu.uade.reclamos.prioridad.proveedor;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Traduce los errores HTTP de cualquier proveedor a las tres fallas del servicio. Los mensajes nunca
 * incluyen la clave ni el texto del reclamo.
 */
final class ErroresProveedor {

    private ErroresProveedor() {
    }

    static RuntimeException traducir(String proveedor, RestClientException error) {
        if (error instanceof RestClientResponseException respuesta) {
            int estado = respuesta.getStatusCode().value();
            String motivo = switch (estado) {
                case 400 -> "rechazó el pedido; revisar la clave y el nombre del modelo";
                case 401, 403 -> "rechazó la clave";
                case 404 -> "no encontró el modelo configurado";
                case 429 -> "no acepta más consultas por ahora: se agotó la cuota";
                default -> "respondió con un error";
            };
            return new ProveedorNoDisponibleException(proveedor + " " + motivo + " (HTTP " + estado + ")", error);
        }
        // Se mira la causa y no el tipo de la excepción de Spring: con HttpURLConnection el corte por tiempo
        // salta recién al leer la respuesta, y RestClient lo entrega como RestClientException genérica, no
        // como ResourceAccessException.
        if (tieneCausa(error, SocketTimeoutException.class) || tieneCausa(error, HttpTimeoutException.class)) {
            return new TiempoAgotadoException(proveedor + " no respondió a tiempo", error);
        }
        // Un JSON ilegible también es una IOException (de Jackson): por eso se buscan solo las fallas de red.
        if (error instanceof ResourceAccessException || tieneCausa(error, SocketException.class)
                || tieneCausa(error, UnknownHostException.class)) {
            return new ProveedorNoDisponibleException(proveedor + " no responde", error);
        }
        return new RespuestaInvalidaException(proveedor + " respondió algo que no se pudo leer", error);
    }

    private static boolean tieneCausa(Throwable error, Class<? extends Throwable> tipo) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (tipo.isInstance(causa)) {
                return true;
            }
        }
        return false;
    }
}
