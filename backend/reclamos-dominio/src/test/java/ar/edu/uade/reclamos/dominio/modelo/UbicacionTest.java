package ar.edu.uade.reclamos.dominio.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.uade.reclamos.comun.excepcion.DatosInvalidosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UbicacionTest {

    @Test
    @DisplayName("Una ubicación pendiente no tiene coordenadas ni proveedor")
    void pendienteSinDatos() {
        Ubicacion pendiente = Ubicacion.pendiente();

        assertEquals(EstadoUbicacion.PENDIENTE, pendiente.getEstado());
        assertFalse(pendiente.isConfirmada());
        assertNull(pendiente.getLatitud());
        assertNull(pendiente.getLongitud());
        assertNull(pendiente.getDireccionNormalizada());
        assertNull(pendiente.getProveedor());
        assertEquals(Ubicacion.pendiente(), pendiente);
    }

    @Test
    @DisplayName("Una ubicación confirmada guarda coordenadas, dirección normalizada y proveedor")
    void confirmadaConDatos() {
        Ubicacion ubicacion = Ubicacion.confirmada(-34.7361, -58.3229, "  GRL BELGRANO 450, Bernal Oeste ", "georef");

        assertTrue(ubicacion.isConfirmada());
        assertEquals(-34.7361, ubicacion.getLatitud());
        assertEquals(-58.3229, ubicacion.getLongitud());
        assertEquals("GRL BELGRANO 450, Bernal Oeste", ubicacion.getDireccionNormalizada());
        assertEquals("georef", ubicacion.getProveedor());
        assertEquals(Ubicacion.confirmada(-34.7361, -58.3229, "GRL BELGRANO 450, Bernal Oeste", "georef"), ubicacion);
    }

    @Test
    @DisplayName("Coordenadas fuera de rango, dirección o proveedor vacíos se rechazan")
    void rechazaDatosInvalidos() {
        assertThrows(DatosInvalidosException.class, () -> Ubicacion.confirmada(-91, 0, "Calle 1", "georef"));
        assertThrows(DatosInvalidosException.class, () -> Ubicacion.confirmada(0, 180.5, "Calle 1", "georef"));
        assertThrows(DatosInvalidosException.class, () -> Ubicacion.confirmada(Double.NaN, 0, "Calle 1", "georef"));
        assertThrows(DatosInvalidosException.class, () -> Ubicacion.confirmada(0, 0, " ", "georef"));
        assertThrows(DatosInvalidosException.class, () -> Ubicacion.confirmada(0, 0, "Calle 1", null));
        assertThrows(DatosInvalidosException.class,
                () -> Ubicacion.confirmada(0, 0, "x".repeat(Ubicacion.LONGITUD_MAXIMA_DIRECCION + 1), "georef"));
    }
}
