package ar.edu.uade.reclamos.aplicacion.rest.configuracion;

import ar.edu.uade.reclamos.aplicacion.rest.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI de la API de reclamos (REST 1), generado a partir de los controladores y los DTOs.
 * Swagger UI queda en {@code /swagger-ui.html} y el contrato en {@code /v3/api-docs.yaml}.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionOpenApi {

    static final String ENCABEZADO_USUARIO = "X-Usuario-Id";
    private static final String ESQUEMA_ERROR = "#/components/schemas/ErrorResponse";

    /** Respuestas de error comunes a todas las operaciones, con el cuerpo de {@link ErrorResponse}. */
    private static final Map<String, String> ERRORES = new LinkedHashMap<>();

    static {
        ERRORES.put("400", "Datos inválidos, encabezado faltante o formato incorrecto (DATOS_INVALIDOS)");
        ERRORES.put("403", "El rol no puede hacer la operación o el reclamo no es suyo (ACCESO_DENEGADO)");
        ERRORES.put("404", "No existe el usuario, reclamo, barrio, categoría o área (RECURSO_NO_ENCONTRADO)");
        ERRORES.put("409", "Regla de negocio o cambio concurrente (REGLA_NEGOCIO, CONFLICTO_CONCURRENTE)");
        ERRORES.put("500", "Error inesperado (ERROR_INTERNO)");
    }

    @Bean
    public OpenAPI apiReclamos() {
        return new OpenAPI().info(new Info()
                .title("Sistema de Reclamos Urbanos — API de reclamos (REST 1)")
                .version("2.0")
                .description("Alta, consulta, cambios de estado y asignación de reclamos urbanos. "
                        + "El usuario que opera se identifica con el encabezado " + ENCABEZADO_USUARIO
                        + ". Fechas en ISO-8601 sin zona horaria."));
    }

    /** Agrega el esquema de error, las respuestas de error y la descripción del encabezado de identidad. */
    @Bean
    public OpenApiCustomizer erroresYEncabezadoComunes() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(ruta -> ruta.readOperations().forEach(this::completar));
        };
    }

    private void completar(Operation operacion) {
        if (operacion.getResponses() == null) {
            operacion.setResponses(new ApiResponses());
        }
        ERRORES.forEach((codigo, descripcion) -> {
            if (!operacion.getResponses().containsKey(codigo)) {
                operacion.getResponses().addApiResponse(codigo, new ApiResponse().description(descripcion)
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref(ESQUEMA_ERROR)))));
            }
        });
        if (operacion.getParameters() != null) {
            operacion.getParameters().stream()
                    .filter(parametro -> ENCABEZADO_USUARIO.equals(parametro.getName()))
                    .forEach(parametro -> parametro.setDescription(
                            "Id del usuario que opera. Reemplaza al login hasta que se implemente."));
        }
    }
}
