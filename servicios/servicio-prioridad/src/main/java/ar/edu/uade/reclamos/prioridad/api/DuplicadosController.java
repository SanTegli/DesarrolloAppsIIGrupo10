package ar.edu.uade.reclamos.prioridad.api;

import ar.edu.uade.reclamos.prioridad.evaluacion.ComparadorDuplicados;
import ar.edu.uade.reclamos.prioridad.evaluacion.ReclamoComparado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tercera operación de REST 2 (PR 27): ¿alguno de los reclamos cercanos es el mismo problema que el nuevo? */
@RestController
@RequestMapping("/api/duplicados")
@Tag(name = "Duplicados", description = "Reclamo cercano que describe el mismo problema, según un modelo de lenguaje")
public class DuplicadosController {

    private final ComparadorDuplicados comparador;

    public DuplicadosController(ComparadorDuplicados comparador) {
        this.comparador = comparador;
    }

    @PostMapping("/comparar")
    @Operation(summary = "Comparar un reclamo nuevo con los reclamos abiertos cercanos",
            description = "Le pide al modelo configurado cuál de los candidatos describe el mismo problema, o "
                    + "ninguno, con una explicación breve. Solo compara texto: categoría y descripción.")
    @ApiResponse(responseCode = "200", description = "El id del candidato más parecido, o null si ninguno es el mismo")
    @ApiResponse(responseCode = "400", description = "Entrada inválida o ids repetidos (DATOS_INVALIDOS)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "El modelo respondió algo inválido (RESPUESTA_INVALIDA)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503",
            description = "Proveedor caído, sin clave o sin cuota (PROVEEDOR_NO_DISPONIBLE)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "504", description = "El modelo no respondió a tiempo (TIEMPO_AGOTADO)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ComparacionDuplicadosResponse comparar(@Valid @RequestBody ComparacionDuplicadosRequest pedido) {
        List<ReclamoComparado> candidatos = pedido.candidatos().stream()
                .map(candidato -> new ReclamoComparado(candidato.id(), candidato.categoria(), candidato.descripcion()))
                .toList();
        if (new HashSet<>(candidatos.stream().map(ReclamoComparado::id).toList()).size() != candidatos.size()) {
            throw new IdsRepetidosException();
        }
        ReclamoComparado nuevo = new ReclamoComparado("nuevo", pedido.nuevo().categoria(), pedido.nuevo().descripcion());
        return ComparacionDuplicadosResponse.de(comparador.comparar(nuevo, candidatos));
    }
}
