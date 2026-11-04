package ar.edu.uade.reclamos.dominio.modelo;

import static ar.edu.uade.reclamos.dominio.DatosDePrueba.AHORA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import ar.edu.uade.reclamos.comun.excepcion.ReglaNegocioException;
import ar.edu.uade.reclamos.dominio.DatosDePrueba;
import org.junit.jupiter.api.Test;

/** Teléfono verificado, su formato y el código de un solo uso. */
class VerificacionTelefonoTest {

    private final DatosDePrueba datos = new DatosDePrueba();

    @Test
    void normalizaElTelefonoEnFormatoInternacional() {
        assertEquals("+5491155550000", Telefono.normalizar("5491155550000"));
        assertEquals("+5491155550000", Telefono.normalizar("+54 9 11 5555-0000"));
        assertThrows(DatosInvalidosException.class, () -> Telefono.normalizar("1234"));
        assertThrows(DatosInvalidosException.class, () -> Telefono.normalizar("+54 9 11 abc"));
        assertThrows(DatosInvalidosException.class, () -> Telefono.normalizar(null));
    }

    @Test
    void enmascaradoSoloMuestraElPaisYLosUltimosCuatro() {
        String enmascarado = Telefono.enmascarar("+5491155551234");

        assertTrue(enmascarado.startsWith("+54 "));
        assertTrue(enmascarado.endsWith("1234"));
        assertFalse(enmascarado.contains("5555"));
        assertEquals(null, Telefono.enmascarar(null));
    }

    @Test
    void elCiudadanoVerificaYCambiaDeNumero() {
        Ciudadano ana = datos.ana;
        assertFalse(ana.tieneTelefonoVerificado());

        ana.verificarTelefono("5491155550000", AHORA);
        assertTrue(ana.tieneTelefonoVerificado());
        assertEquals("+5491155550000", ana.getTelefonoVerificado());

        ana.verificarTelefono("+5491166660000", AHORA.plusDays(1));
        assertEquals("+5491166660000", ana.getTelefonoVerificado());
        assertEquals(AHORA.plusDays(1), ana.getFechaVerificacionTelefono());
    }

    @Test
    void bloquearNoTocaNadaMasQueElBloqueo() {
        Ciudadano ana = datos.ana;
        ana.bloquear();
        assertTrue(ana.isBloqueado());
        assertTrue(ana.isActivo());
        ana.desbloquear();
        assertFalse(ana.isBloqueado());
    }

    @Test
    void elCodigoSirveUnaSolaVezYVence() {
        CodigoVerificacion codigo = new CodigoVerificacion("RU4K7P2Q", datos.ana, AHORA, AHORA.plusMinutes(10));
        assertTrue(codigo.estaVigente(AHORA.plusMinutes(10)));
        assertTrue(codigo.perteneceA(datos.ana));
        assertFalse(codigo.perteneceA(datos.bruno));

        codigo.usar(AHORA.plusMinutes(5));

        assertFalse(codigo.estaVigente(AHORA.plusMinutes(6)));
        assertThrows(ReglaNegocioException.class, () -> codigo.usar(AHORA.plusMinutes(6)));

        CodigoVerificacion vencido = new CodigoVerificacion("ABCD2345", datos.ana, AHORA, AHORA.plusMinutes(10));
        assertFalse(vencido.estaVigente(AHORA.plusMinutes(11)));
        assertThrows(ReglaNegocioException.class, () -> vencido.usar(AHORA.plusMinutes(11)));
    }
}
