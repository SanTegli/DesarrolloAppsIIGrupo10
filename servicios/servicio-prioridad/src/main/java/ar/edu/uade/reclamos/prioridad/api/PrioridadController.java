package ar.edu.uade.reclamos.prioridad.api;

import ar.edu.uade.reclamos.prioridad.evaluacion.EvaluadorPrioridad;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prioridades")
@Tag(name = "Prioridades", description = "Prioridad sugerida por un modelo de lenguaje")
public class PrioridadController {

    private final EvaluadorPrioridad evaluador;

    public PrioridadController(EvaluadorPrioridad evaluador) {
        this.evaluador = evaluador;
    }

    @PostMapping("/evaluar")
    @Operation(summary = "Sugerir la prioridad de un reclamo",
            description = "Le pide al modelo configurado una prioridad (BAJA, MEDIA, ALTA o CRITICA) y una "
                    + "explicación breve. Es una sugerencia: el backend la combina con la prioridad de la categoría.")
    @ApiResponse(responseCode = "200", description = "Prioridad sugerida")
    @ApiResponse(responseCode = "400", description = "Entrada inválida (DATOS_INVALIDOS)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "El modelo respondió algo inválido (RESPUESTA_INVALIDA)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503",
            description = "Proveedor caído, sin clave o sin cuota (PROVEEDOR_NO_DISPONIBLE)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "504", description = "El modelo no respondió a tiempo (TIEMPO_AGOTADO)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public EvaluacionResponse evaluar(@Valid @RequestBody EvaluacionRequest pedido) {
        return EvaluacionResponse.de(evaluador.evaluar(pedido.categoria(), pedido.descripcion()));
    }
}
