package ar.edu.uade.reclamos.aplicacion.servicio;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.aplicacion.facade.GestionReclamosFacade;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransaccionesServiceTest {
    @Test
    void cadaOperacionPublicaDeEscrituraEsTransaccional() {
        for (var metodo : ReclamoService.class.getDeclaredMethods()) {
            if (Modifier.isPublic(metodo.getModifiers())) {
                Transactional tx = metodo.getAnnotation(Transactional.class);
                assertThat(tx).as(metodo.getName()).isNotNull();
                assertThat(tx.readOnly()).as(metodo.getName()).isFalse();
            }
        }
    }

    @Test
    void consultasSonReadOnlyYFacadeNoDefineTransacciones() {
        assertThat(ConsultaReclamosService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(ConsultaCatalogosService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(GestionReclamosFacade.class.getAnnotation(Transactional.class)).isNull();
        for (var metodo : GestionReclamosFacade.class.getDeclaredMethods()) {
            assertThat(metodo.getAnnotation(Transactional.class)).as(metodo.getName()).isNull();
        }
    }
}
