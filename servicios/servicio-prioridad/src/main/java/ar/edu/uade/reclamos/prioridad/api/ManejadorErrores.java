package ar.edu.uade.reclamos.prioridad.api;

import ar.edu.uade.reclamos.prioridad.proveedor.ProveedorNoDisponibleException;
import ar.edu.uade.reclamos.prioridad.proveedor.RespuestaInvalidaException;
import ar.edu.uade.reclamos.prioridad.proveedor.TiempoAgotadoException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cada falla del proveedor tiene su código HTTP, así el backend sabe qué pasó: 502 si el modelo contestó mal,
 * 503 si el proveedor no se puede usar y 504 si tardó demasiado. En los tres casos el backend usa el respaldo.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> datosInvalidos(MethodArgumentNotValidException error,
                                                        HttpServletRequest pedido) {
        var campos = new TreeMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(campo ->
                campos.putIfAbsent(campo.getField(), campo.getField() + ": " + campo.getDefaultMessage()));
        return respuesta(400, "DATOS_INVALIDOS", "Los datos recibidos no son válidos.", List.copyOf(campos.values()),
                pedido);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> cuerpoIlegible(Exception error, HttpServletRequest pedido) {
        return respuesta(400, "DATOS_INVALIDOS", "El cuerpo tiene que ser un JSON con categoria y descripcion.",
                List.of(), pedido);
    }

    @ExceptionHandler(RespuestaInvalidaException.class)
    public ResponseEntity<ErrorResponse> respuestaInvalida(RespuestaInvalidaException error, HttpServletRequest pedido) {
        LOGGER.warn("Respuesta inválida del modelo: {}", error.getMessage());
        return respuesta(502, "RESPUESTA_INVALIDA", error.getMessage(), List.of(), pedido);
    }

    @ExceptionHandler(ProveedorNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> proveedorNoDisponible(ProveedorNoDisponibleException error,
                                                               HttpServletRequest pedido) {
        LOGGER.warn("Proveedor de IA no disponible: {}", error.getMessage());
        return respuesta(503, "PROVEEDOR_NO_DISPONIBLE", error.getMessage(), List.of(), pedido);
    }

    @ExceptionHandler(TiempoAgotadoException.class)
    public ResponseEntity<ErrorResponse> tiempoAgotado(TiempoAgotadoException error, HttpServletRequest pedido) {
        LOGGER.warn("Proveedor de IA sin respuesta: {}", error.getMessage());
        return respuesta(504, "TIEMPO_AGOTADO", error.getMessage(), List.of(), pedido);
    }

    private static ResponseEntity<ErrorResponse> respuesta(int status, String codigo, String mensaje,
                                                           List<String> detalles, HttpServletRequest pedido) {
        return ResponseEntity.status(status).body(new ErrorResponse(status, codigo, mensaje, detalles,
                pedido.getRequestURI(), LocalDateTime.now()));
    }
}
