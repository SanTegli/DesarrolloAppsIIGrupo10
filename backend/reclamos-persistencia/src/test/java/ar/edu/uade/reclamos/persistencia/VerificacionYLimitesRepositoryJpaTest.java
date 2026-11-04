package ar.edu.uade.reclamos.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.uade.reclamos.dominio.modelo.Ciudadano;
import ar.edu.uade.reclamos.dominio.modelo.CodigoVerificacion;
import ar.edu.uade.reclamos.dominio.modelo.Usuario;
import ar.edu.uade.reclamos.dominio.repositorio.CodigoVerificacionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Teléfono verificado, bloqueo, códigos de verificación y los conteos de los límites de uso. */
class VerificacionYLimitesRepositoryJpaTest extends PersistenciaTestBase {
    @Autowired CodigoVerificacionRepository codigos;

    @Test
    void guardaElTelefonoVerificadoYElBloqueoYBuscaPorTelefono() {
        prepararDatos();
        ciudadano.verificarTelefono("5491155550000", AHORA);
        ciudadano.bloquear();
        usuarios.guardar(ciudadano);
        flushYLimpiar();

        Ciudadano encontrado = usuarios.buscarPorTelefonoVerificado("+5491155550000").orElseThrow();
        assertThat(encontrado.getId()).isEqualTo(ciudadano.getId());
        assertThat(encontrado.getFechaVerificacionTelefono()).isEqualTo(AHORA);
        assertThat(encontrado.isBloqueado()).isTrue();
        assertThat(usuarios.buscarPorTelefonoVerificado("+5491199999999")).isEmpty();
        assertThat(usuarios.buscarPorTelefonoVerificado(null)).isEmpty();
        Usuario agenteGuardado = usuarios.buscarPorId(agente.getId()).orElseThrow();
        assertThat(agenteGuardado.isBloqueado()).isFalse();
    }

    @Test
    void unNumeroNoPuedeEstarVerificadoEnDosCuentas() {
        prepararDatos();
        ciudadano.verificarTelefono("+5491155550000", AHORA);
        usuarios.guardar(ciudadano);
        em.flush();
        Ciudadano otro = new Ciudadano("31222333", "Bruno", "Díaz", "bruno@example.org", null);
        otro.verificarTelefono("+5491155550000", AHORA);

        assertThatThrownBy(() -> {
            usuarios.guardar(otro);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guardaLosCodigosYCuentaLosDeLaUltimaHora() {
        prepararDatos();
        codigos.guardar(new CodigoVerificacion("AAAA2222", ciudadano, AHORA.minusHours(2), AHORA.minusHours(2).plusMinutes(10)));
        codigos.guardar(new CodigoVerificacion("BBBB3333", ciudadano, AHORA.minusMinutes(30), AHORA.minusMinutes(20)));
        CodigoVerificacion vigente = codigos.guardar(
                new CodigoVerificacion("CCCC4444", ciudadano, AHORA, AHORA.plusMinutes(10)));
        vigente.usar(AHORA.plusMinutes(1));
        codigos.guardar(vigente);
        flushYLimpiar();

        assertThat(codigos.contarCreadosPorCiudadanoDesde(ciudadano.getId(), AHORA.minusHours(1))).isEqualTo(2);
        CodigoVerificacion leido = codigos.buscarPorCodigo("CCCC4444").orElseThrow();
        assertThat(leido.getCiudadano().getId()).isEqualTo(ciudadano.getId());
        assertThat(leido.getFechaUso()).isEqualTo(AHORA.plusMinutes(1));
        assertThat(codigos.buscarPorCodigo("ZZZZ9999")).isEmpty();
        assertThat(codigos.buscarPorCodigo(null)).isEmpty();
    }

    @Test
    void cuentaLosReclamosQuePresentoElCiudadanoDesdeUnaFecha() {
        prepararDatos();
        reclamos.guardar(nuevoReclamo());
        reclamos.guardar(nuevoReclamo());
        flushYLimpiar();

        // nuevoReclamo() usa un reloj fijo en AHORA (2026-10-05 10:00, UTC).
        assertThat(reclamos.contarCreadosPorCiudadanoDesde(ciudadano.getId(), AHORA.minusHours(1))).isEqualTo(2);
        assertThat(reclamos.contarCreadosPorCiudadanoDesde(ciudadano.getId(), AHORA.plusHours(1))).isZero();
        assertThat(reclamos.contarCreadosPorCiudadanoDesde(agente.getId(), AHORA.minusHours(1))).isZero();
    }
}
