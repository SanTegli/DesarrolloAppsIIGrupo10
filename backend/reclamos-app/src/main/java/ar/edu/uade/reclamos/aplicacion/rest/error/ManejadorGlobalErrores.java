package ar.edu.uade.reclamos.aplicacion.rest.error;

import ar.edu.uade.reclamos.aplicacion.rest.dto.ErrorResponse;
import ar.edu.uade.reclamos.comun.excepcion.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ManejadorGlobalErrores {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManejadorGlobalErrores.class);
    private final Clock reloj;

    public ManejadorGlobalErrores(Clock reloj) {
        this.reloj = reloj;
    }

    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<ErrorResponse> datosInvalidos(DatosInvalidosException error, HttpServletRequest request) {
        return respuesta(400, "DATOS_INVALIDOS", error.getMessage(), List.of(), request);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException error, HttpServletRequest request) {
        return respuesta(403, "ACCESO_DENEGADO", error.getMessage(), List.of(), request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> recursoNoEncontrado(RecursoNoEncontradoException error, HttpServletRequest request) {
        return respuesta(404, "RECURSO_NO_ENCONTRADO", error.getMessage(), List.of(), request);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> reglaNegocio(ReglaNegocioException error, HttpServletRequest request) {
        return respuesta(409, "REGLA_NEGOCIO", error.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> cuerpoInvalido(MethodArgumentNotValidException error, HttpServletRequest request) {
        var campos = new TreeMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(campo ->
                campos.putIfAbsent(campo.getField(), campo.getField() + ": " + campo.getDefaultMessage()));
        return respuesta(400, "DATOS_INVALIDOS", "Los datos recibidos no son validos.",
                List.copyOf(campos.values()), request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> parametrosInvalidos(HandlerMethodValidationException error,
                                                            HttpServletRequest request) {
        var campos = new TreeMap<String, String>();
        error.getParameterValidationResults().forEach(resultado -> {
            if (resultado instanceof ParameterErrors cuerpo) {
                cuerpo.getFieldErrors().forEach(detalle -> campos.putIfAbsent(detalle.getField(),
                        detalle.getField() + ": " + detalle.getDefaultMessage()));
            } else {
                String campo = resultado.getMethodParameter().getParameterName();
                resultado.getResolvableErrors().forEach(detalle ->
                        campos.putIfAbsent(campo, campo + ": " + detalle.getDefaultMessage()));
            }
        });
        return respuesta(400, "DATOS_INVALIDOS", "Los parametros recibidos no son validos.",
                List.copyOf(campos.values()), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> validacionInvalida(ConstraintViolationException error, HttpServletRequest request) {
        var campos = new TreeMap<String, String>();
        error.getConstraintViolations().forEach(detalle -> {
            String campo = detalle.getPropertyPath().toString();
            campos.putIfAbsent(campo, campo + ": " + detalle.getMessage());
        });
        return respuesta(400, "DATOS_INVALIDOS", "Los datos recibidos no son validos.",
                List.copyOf(campos.values()), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class,
            ServletRequestBindingException.class})
    public ResponseEntity<ErrorResponse> formatoInvalido(Exception error, HttpServletRequest request) {
        return respuesta(400, "DATOS_INVALIDOS", "Faltan datos obligatorios o su formato es invalido.",
                List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException error, HttpServletRequest request) {
        return respuesta(404, "RECURSO_NO_ENCONTRADO", "El recurso solicitado no existe.", List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException error,
                                                         HttpServletRequest request) {
        ResponseEntity<ErrorResponse> respuesta = respuesta(405, "METODO_NO_PERMITIDO",
                "El método HTTP utilizado no está permitido para este recurso.", List.of(), request);
        return new ResponseEntity<>(respuesta.getBody(), error.getHeaders(), respuesta.getStatusCode());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> tipoContenidoNoSoportado(HttpMediaTypeNotSupportedException error,
                                                                HttpServletRequest request) {
        ResponseEntity<ErrorResponse> respuesta = respuesta(415, "TIPO_CONTENIDO_NO_SOPORTADO",
                "El tipo de contenido enviado no está soportado para este recurso.", List.of(), request);
        return new ResponseEntity<>(respuesta.getBody(), error.getHeaders(), respuesta.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception error, HttpServletRequest request) {
        LOGGER.error("Error inesperado en {}", request.getRequestURI(), error);
        return respuesta(500, "ERROR_INTERNO", "Ocurrio un error interno. Intentalo nuevamente.",
                List.of(), request);
    }

    private ResponseEntity<ErrorResponse> respuesta(int status, String codigo, String mensaje,
                                                    List<String> detalles, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(status, codigo, mensaje, detalles,
                request.getRequestURI(), LocalDateTime.now(reloj)));
    }
}
