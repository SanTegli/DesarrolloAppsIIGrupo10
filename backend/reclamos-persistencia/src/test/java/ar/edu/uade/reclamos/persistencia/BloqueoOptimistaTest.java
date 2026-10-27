package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.*;
import ar.edu.uade.reclamos.dominio.modelo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Dos operaciones leen el mismo reclamo; la que guarda segunda no pisa el cambio de la primera. */
class BloqueoOptimistaTest extends PersistenciaTestBase {
    @Autowired PlatformTransactionManager transacciones;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void unaCopiaDesactualizadaNoPisaElCambioDeOtraOperacion() {
        String numero = new TransactionTemplate(transacciones).execute(status -> {
            prepararDatos();
            return reclamos.guardar(nuevoReclamo()).getNumero();
        });
        Reclamo primera = reclamos.buscarPorNumero(numero).orElseThrow();
        Reclamo segunda = reclamos.buscarPorNumero(numero).orElseThrow();

        primera.asignarArea(area, null, "Primera operación", AHORA);
        reclamos.guardar(primera);
        segunda.asignarArea(area, null, "Segunda operación", AHORA);

        assertThatThrownBy(() -> reclamos.guardar(segunda)).isInstanceOf(OptimisticLockingFailureException.class);
        Reclamo actual = reclamos.buscarPorNumero(numero).orElseThrow();
        assertThat(actual.getHistorial()).extracting(HistorialEstado::getObservacion)
                .containsExactly("Reclamo ingresado", "Primera operación");
    }
}
