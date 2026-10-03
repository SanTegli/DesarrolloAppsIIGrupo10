package ar.edu.uade.reclamos.aplicacion.evento;

import static org.mockito.Mockito.*;

import ar.edu.uade.reclamos.dominio.evento.ReclamoCreado;
import ar.edu.uade.reclamos.dominio.modelo.Prioridad;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PublicadorEventosSpringTest {
    @Test
    void publicaLaMismaInstanciaDelEventoSinOtrasInteracciones() {
        ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
        var evento = new ReclamoCreado("REC-12345678", 1L, 2L, 3L, Prioridad.ALTA,
                LocalDateTime.of(2026, 10, 5, 10, 0));
        new PublicadorEventosSpring(spring).publicar(evento);
        verify(spring).publishEvent(same(evento));
        verifyNoMoreInteractions(spring);
    }
}
