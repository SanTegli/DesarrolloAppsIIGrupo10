package ar.edu.uade.reclamos.prioridad.api;

import ar.edu.uade.reclamos.prioridad.evaluacion.SugeridorCategoria;
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

/** Segunda operación de REST 2 (PR 26): la categoría de un reclamo cargado como "Otro problema". */
@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categorías", description = "Categoría sugerida por un modelo de lenguaje para un reclamo de \"Otro problema\"")
public class CategoriaController {

    private final SugeridorCategoria sugeridor;

    public CategoriaController(SugeridorCategoria sugeridor) {
        this.sugeridor = sugeridor;
    }

    @PostMapping("/sugerir")
    @Operation(summary = "Sugerir la categoría de un reclamo de \"Otro problema\"",
            description = "Le pide al modelo configurado cuál de las categorías recibidas corresponde, o ninguna, "
                    + "con una explicación breve. Es una sugerencia: la recategorización la decide un administrador.")
    @ApiResponse(responseCode = "200", description = "Categoría sugerida, o null si ninguna corresponde")
    @ApiResponse(responseCode = "400", description = "Entrada inválida (DATOS_INVALIDOS)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "El modelo respondió algo inválido (RESPUESTA_INVALIDA)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503",
            description = "Proveedor caído, sin clave o sin cuota (PROVEEDOR_NO_DISPONIBLE)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "504", description = "El modelo no respondió a tiempo (TIEMPO_AGOTADO)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public SugerenciaCategoriaResponse sugerir(@Valid @RequestBody SugerenciaCategoriaRequest pedido) {
        return SugerenciaCategoriaResponse.de(sugeridor.sugerir(pedido.categoriaPropuesta(), pedido.descripcion(),
                pedido.categorias()));
    }
}
