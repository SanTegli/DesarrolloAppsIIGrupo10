package ar.edu.uade.reclamos.aplicacion.rest;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.uade.reclamos.aplicacion.rest.dto.ErrorResponse;
import ar.edu.uade.reclamos.aplicacion.rest.error.ManejadorGlobalErrores;
import ar.edu.uade.reclamos.dominio.modelo.Reclamo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ConflictoConcurrenteTest {

    @Test
    void unaCopiaDesactualizadaDevuelve409ConElCuerpoDeErrorComun() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC);
        MockHttpServletRequest pedido = new MockHttpServletRequest("PATCH", "/api/reclamos/REC-12345678/estado");

        ResponseEntity<ErrorResponse> respuesta = new ManejadorGlobalErrores(reloj)
                .conflictoConcurrente(new ObjectOptimisticLockingFailureException(Reclamo.class, 1L), pedido);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        ErrorResponse error = respuesta.getBody();
        assertThat(error.status()).isEqualTo(409);
        assertThat(error.codigo()).isEqualTo("CONFLICTO_CONCURRENTE");
        assertThat(error.mensaje()).contains("Volvé a cargarlo");
        assertThat(error.detalles()).isEmpty();
        assertThat(error.ruta()).isEqualTo("/api/reclamos/REC-12345678/estado");
        assertThat(error.fecha()).isEqualTo(LocalDateTime.of(2026, 10, 26, 15, 0));
    }
}
