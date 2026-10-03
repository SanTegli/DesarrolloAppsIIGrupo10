package ar.edu.uade.reclamos.comun.validacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidadorTest {

    @Test
    @DisplayName("Un texto válido se devuelve sin espacios sobrantes")
    void requerirTextoDevuelveElTextoLimpio() {
        assertEquals("Bache en la esquina", Validador.requerirTexto("  Bache en la esquina ", "descripción"));
    }

    @Test
    @DisplayName("Texto nulo, vacío o en blanco se rechaza")
    void requerirTextoRechazaVacios() {
        assertThrows(DatosInvalidosException.class, () -> Validador.requerirTexto(null, "descripción"));
        assertThrows(DatosInvalidosException.class, () -> Validador.requerirTexto("", "descripción"));
        assertThrows(DatosInvalidosException.class, () -> Validador.requerirTexto("   ", "descripción"));
    }

    @Test
    @DisplayName("Un texto más largo que el máximo se rechaza")
    void requerirTextoRespetaLaLongitudMaxima() {
        assertEquals("hola", Validador.requerirTexto("hola", "campo", 4));
        assertThrows(DatosInvalidosException.class, () -> Validador.requerirTexto("holaa", "campo", 4));
    }

    @Test
    @DisplayName("El DNI debe tener 7 u 8 dígitos")
    void validaDni() {
        assertTrue(Validador.esDniValido("1234567"));
        assertTrue(Validador.esDniValido("40123456"));
        assertFalse(Validador.esDniValido("123"));
        assertFalse(Validador.esDniValido("40.123.456"));
        assertFalse(Validador.esDniValido(null));
        assertThrows(DatosInvalidosException.class, () -> Validador.requerirDni("123"));
    }

    @Test
    @DisplayName("El email debe tener usuario, arroba y dominio")
    void validaEmail() {
        assertTrue(Validador.esEmailValido("ana.perez@uade.edu.ar"));
        assertFalse(Validador.esEmailValido("ana.perez"));
        assertFalse(Validador.esEmailValido("ana@dominio"));
        assertThrows(DatosInvalidosException.class, () -> Validador.requerirEmail("email-invalido"));
    }

    @Test
    @DisplayName("Nulos y números no positivos se rechazan")
    void requerirNoNuloYPositivo() {
        assertEquals(48, Validador.requerirPositivo(48, "slaHoras"));
        assertThrows(DatosInvalidosException.class, () -> Validador.requerirPositivo(0, "slaHoras"));
        assertThrows(DatosInvalidosException.class, () -> Validador.requerirNoNulo(null, "categoría"));
    }

    @Test
    @DisplayName("El mensaje de error nombra el campo")
    void elMensajeNombraElCampo() {
        DatosInvalidosException error =
                assertThrows(DatosInvalidosException.class, () -> Validador.requerirTexto(" ", "dirección"));
        assertTrue(error.getMessage().contains("dirección"));
    }
}
